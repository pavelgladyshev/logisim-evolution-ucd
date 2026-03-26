package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.riscv.cpu.PermissionCheck;
import com.cburch.logisim.riscv.cpu.TranslationLookasideBuffer;
import com.cburch.logisim.riscv.cpu.TranslationLookasideBuffer.AccessType;
import com.cburch.logisim.riscv.cpu.rv32imData;
import com.cburch.logisim.riscv.cpu.csrs.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.cburch.logisim.riscv.cpu.TranslationLookasideBuffer.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive SV32 virtual memory tests covering:
 *
 * 1. SATP CSR — fields, MODE, ASID, PPN, root page table address
 * 2. MSTATUS/SSTATUS — SUM, MXR fields and masking
 * 3. PTE permission checks (PermissionCheck.check):
 *    - A/D bit enforcement (Spec §4.3.2 step 6)
 *    - U-bit privilege isolation (Spec §4.3.2 step 6)
 *    - MXR support (Spec §4.3.2 step 6)
 *    - R/W/X access type enforcement
 * 4. TLB — translate, insert, ASID isolation, global pages, megapages,
 *    invalidation (all/address/ASID/address+ASID), LRU eviction, fast-path cache
 * 5. Address translation integration — isTranslationEnabled, checkTlbPermissions
 * 6. SFENCE.VMA instruction encoding and execution
 * 7. SRET / MRET privilege restoration
 * 8. Page fault trap delegation and stval/mtval
 * 9. Edge cases and boundary conditions
 */
class Sv32Test {

    // ================================================================
    //  Common permission bit combos
    // ================================================================
    static final int KERN_RWX = PERM_R | PERM_W | PERM_X | PERM_A | PERM_D;
    static final int KERN_RX  = PERM_R | PERM_X | PERM_A;
    static final int KERN_RW  = PERM_R | PERM_W | PERM_A | PERM_D;
    static final int KERN_X   = PERM_X | PERM_A;
    static final int USER_RWX = KERN_RWX | PERM_U;
    static final int USER_RX  = KERN_RX  | PERM_U;
    static final int USER_RW  = KERN_RW  | PERM_U;
    static final int USER_X   = KERN_X   | PERM_U;

    // ================================================================
    //  1. SATP CSR Tests
    // ================================================================
    @Nested
    class SatpTests {

        @Test
        void modeBitControlsSv32() {
            SATP_CSR satp = new SATP_CSR(0);
            assertFalse(satp.isSV32Enabled(), "Bare mode by default");
            satp.MODE.set(1);
            assertTrue(satp.isSV32Enabled(), "SV32 after MODE=1");
            satp.MODE.set(0);
            assertFalse(satp.isSV32Enabled(), "Bare after MODE=0");
        }

        @Test
        void asid9Bits() {
            SATP_CSR satp = new SATP_CSR(0);
            satp.ASID.set(0);
            assertEquals(0, satp.ASID.get());
            satp.ASID.set(0x1FF);
            assertEquals(0x1FF, satp.ASID.get(), "Max ASID = 511");
        }

        @Test
        void ppn22Bits() {
            SATP_CSR satp = new SATP_CSR(0);
            satp.PPN.set(0x3FFFFF);
            assertEquals(0x3FFFFF, satp.PPN.get(), "Max PPN");
        }

        @Test
        void rootPageTableAddressShifts() {
            SATP_CSR satp = new SATP_CSR(0);
            satp.PPN.set(0x80000);
            assertEquals(0x80000000L, satp.getRootPageTableAddress());
            satp.PPN.set(1);
            assertEquals(0x1000L, satp.getRootPageTableAddress());
        }

        @Test
        void fieldsAreIndependent() {
            SATP_CSR satp = new SATP_CSR(0);
            satp.MODE.set(1);
            satp.ASID.set(0x1AB);
            satp.PPN.set(0x12345);
            assertEquals(1, satp.MODE.get());
            assertEquals(0x1AB, satp.ASID.get());
            assertEquals(0x12345, satp.PPN.get());
        }

        @Test
        void fullValueRoundTrip() {
            SATP_CSR satp = new SATP_CSR(0);
            // MODE=1, ASID=0x1FF, PPN=0x3FFFFF → all bits set
            long allSet = (1L << 31) | (0x1FFL << 22) | 0x3FFFFF;
            satp.write(allSet);
            assertEquals(1, satp.MODE.get());
            assertEquals(0x1FF, satp.ASID.get());
            assertEquals(0x3FFFFF, satp.PPN.get());
        }
    }

    // ================================================================
    //  2. MSTATUS / SSTATUS SUM and MXR Tests
    // ================================================================
    @Nested
    class MstatusTests {

        @Test
        void sumFieldBit18() {
            MSTATUS_CSR mstatus = new MSTATUS_CSR(0);
            assertEquals(0, mstatus.SUM.get());
            mstatus.SUM.set(1);
            assertEquals(1, mstatus.SUM.get());
            assertTrue((mstatus.read() & (1L << 18)) != 0, "SUM should be bit 18");
            mstatus.SUM.set(0);
            assertEquals(0, mstatus.SUM.get());
        }

        @Test
        void mxrFieldBit19() {
            MSTATUS_CSR mstatus = new MSTATUS_CSR(0);
            assertEquals(0, mstatus.MXR.get());
            mstatus.MXR.set(1);
            assertEquals(1, mstatus.MXR.get());
            assertTrue((mstatus.read() & (1L << 19)) != 0, "MXR should be bit 19");
        }

        @Test
        void sumMxrDoNotAffectOtherFields() {
            MSTATUS_CSR mstatus = new MSTATUS_CSR(0);
            mstatus.MIE.set(1);
            mstatus.SUM.set(1);
            mstatus.MXR.set(1);
            assertEquals(1, mstatus.MIE.get(), "MIE should be unaffected");
            assertEquals(1, mstatus.SUM.get());
            assertEquals(1, mstatus.MXR.get());
        }

        @Test
        void sstatusExposesSumAndMxr() {
            MSTATUS_CSR mstatus = new MSTATUS_CSR(0);
            SSTATUS_CSR sstatus = new SSTATUS_CSR(mstatus);

            // Set SUM and MXR via mstatus
            mstatus.SUM.set(1);
            mstatus.MXR.set(1);

            // Verify sstatus can read them
            long sval = sstatus.read();
            assertTrue((sval & (1L << 18)) != 0, "SSTATUS should expose SUM");
            assertTrue((sval & (1L << 19)) != 0, "SSTATUS should expose MXR");
        }

        @Test
        void sstatusWritesSumAndMxrThroughToMstatus() {
            MSTATUS_CSR mstatus = new MSTATUS_CSR(0);
            SSTATUS_CSR sstatus = new SSTATUS_CSR(mstatus);

            // Write SUM and MXR through sstatus
            sstatus.write((1L << 18) | (1L << 19));

            assertEquals(1, mstatus.SUM.get(), "SUM should be set via sstatus");
            assertEquals(1, mstatus.MXR.get(), "MXR should be set via sstatus");
        }

        @Test
        void sstatusMasksMachineFields() {
            MSTATUS_CSR mstatus = new MSTATUS_CSR(0);
            SSTATUS_CSR sstatus = new SSTATUS_CSR(mstatus);

            mstatus.MIE.set(1);   // bit 3 — machine only
            mstatus.MPIE.set(1);  // bit 7 — machine only
            mstatus.MPP.set(3);   // bits 11-12 — machine only

            long sval = sstatus.read();
            assertEquals(0, sval & (1L << 3), "SSTATUS should not expose MIE");
            assertEquals(0, sval & (1L << 7), "SSTATUS should not expose MPIE");
            assertEquals(0, sval & (3L << 11), "SSTATUS should not expose MPP");
        }

        @Test
        void sstatusWriteDoesNotClobberMachineFields() {
            MSTATUS_CSR mstatus = new MSTATUS_CSR(0);
            SSTATUS_CSR sstatus = new SSTATUS_CSR(mstatus);

            mstatus.MIE.set(1);
            mstatus.MPP.set(3);

            // Write 0 through sstatus — should not clear MIE or MPP
            sstatus.write(0);

            assertEquals(1, mstatus.MIE.get(), "MIE must survive sstatus write");
            assertEquals(3, mstatus.MPP.get(), "MPP must survive sstatus write");
        }
    }

    // ================================================================
    //  3. Permission Check Tests (PermissionCheck.check)
    // ================================================================
    @Nested
    class PermissionTests {

        // ---------- A/D bit enforcement ----------

        @Test
        void accessedBitZero_FaultsAllAccessTypes() {
            int perms = PERM_R | PERM_W | PERM_X | PERM_D; // A=0
            for (AccessType t : AccessType.values()) {
                assertFalse(PermissionCheck.check(perms, t, PRIVILEGE_MODE.SUPERVISOR, 0, 0),
                    "A=0 should fault for " + t);
            }
        }

        @Test
        void dirtyBitZero_FaultsStoreOnly() {
            int perms = PERM_R | PERM_W | PERM_X | PERM_A; // D=0
            assertTrue(PermissionCheck.check(perms, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 0, 0),
                "D=0 should not fault FETCH");
            assertTrue(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0),
                "D=0 should not fault LOAD");
            assertFalse(PermissionCheck.check(perms, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0),
                "D=0 should fault STORE");
        }

        @Test
        void bothAdSet_StoreSucceeds() {
            assertTrue(PermissionCheck.check(KERN_RWX, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
        }

        @Test
        void accessedSetDirtyNotNeededForFetch() {
            int perms = PERM_X | PERM_A; // no D
            assertTrue(PermissionCheck.check(perms, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
        }

        @Test
        void accessedSetDirtyNotNeededForLoad() {
            int perms = PERM_R | PERM_A; // no D
            assertTrue(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
        }

        // ---------- U-bit privilege isolation ----------

        @Test
        void supervisorPage_DeniedInUserMode_AllTypes() {
            for (AccessType t : AccessType.values()) {
                assertFalse(PermissionCheck.check(KERN_RWX, t, PRIVILEGE_MODE.USER, 0, 0),
                    "U=0 page should deny U-mode " + t);
            }
        }

        @Test
        void supervisorPage_AllowedInSupervisorMode() {
            assertTrue(PermissionCheck.check(KERN_RWX, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
            assertTrue(PermissionCheck.check(KERN_RWX, AccessType.LOAD,  PRIVILEGE_MODE.SUPERVISOR, 0, 0));
            assertTrue(PermissionCheck.check(KERN_RWX, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
        }

        @Test
        void userPage_AllowedInUserMode() {
            assertTrue(PermissionCheck.check(USER_RWX, AccessType.FETCH, PRIVILEGE_MODE.USER, 0, 0));
            assertTrue(PermissionCheck.check(USER_RWX, AccessType.LOAD,  PRIVILEGE_MODE.USER, 0, 0));
            assertTrue(PermissionCheck.check(USER_RWX, AccessType.STORE, PRIVILEGE_MODE.USER, 0, 0));
        }

        @Test
        void userPage_DeniedInSMode_SumOff() {
            assertFalse(PermissionCheck.check(USER_RWX, AccessType.LOAD,  PRIVILEGE_MODE.SUPERVISOR, 0, 0));
            assertFalse(PermissionCheck.check(USER_RWX, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
            assertFalse(PermissionCheck.check(USER_RWX, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
        }

        @Test
        void userPage_LoadStoreAllowedInSMode_SumOn() {
            assertTrue(PermissionCheck.check(USER_RWX, AccessType.LOAD,  PRIVILEGE_MODE.SUPERVISOR, 1, 0));
            assertTrue(PermissionCheck.check(USER_RWX, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 1, 0));
        }

        @Test
        void userPage_FetchDeniedInSMode_EvenWithSum() {
            assertFalse(PermissionCheck.check(USER_RWX, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 1, 0),
                "SUM does not permit S-mode FETCH from U-page");
        }

        @Test
        void userPage_SumOnlyAffectsSMode() {
            // SUM=0 or SUM=1 should not matter for U-mode on U-pages
            assertTrue(PermissionCheck.check(USER_RWX, AccessType.LOAD, PRIVILEGE_MODE.USER, 0, 0));
            assertTrue(PermissionCheck.check(USER_RWX, AccessType.LOAD, PRIVILEGE_MODE.USER, 1, 0));
        }

        // ---------- MXR (Make eXecutable Readable) ----------

        @Test
        void mxrOff_ExecuteOnlyDeniesLoad() {
            assertFalse(PermissionCheck.check(KERN_X, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
        }

        @Test
        void mxrOn_ExecuteOnlyAllowsLoad() {
            assertTrue(PermissionCheck.check(KERN_X, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 1));
        }

        @Test
        void mxrOn_NoXNoR_StillDeniesLoad() {
            int perms = PERM_W | PERM_A | PERM_D; // W only, no R, no X
            assertFalse(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 1),
                "MXR only helps when X=1");
        }

        @Test
        void mxrDoesNotAffectFetch() {
            // R-only page, MXR=1 — fetch should still require X
            int perms = PERM_R | PERM_A;
            assertFalse(PermissionCheck.check(perms, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 0, 1));
        }

        @Test
        void mxrDoesNotAffectStore() {
            // X-only page, MXR=1 — store should still require W
            assertFalse(PermissionCheck.check(KERN_X, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 1));
        }

        @Test
        void mxrWithUserPage_SModeSumOn() {
            // U=1, X=1, R=0, S-mode, SUM=1, MXR=1 → load allowed
            int perms = PERM_X | PERM_U | PERM_A;
            assertTrue(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 1, 1));
        }

        @Test
        void mxrWithUserPage_SModeSumOff_Denied() {
            // U=1, X=1, R=0, S-mode, SUM=0, MXR=1 → denied (U-bit blocks before MXR)
            int perms = PERM_X | PERM_U | PERM_A;
            assertFalse(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 1));
        }

        // ---------- R/W/X basic enforcement ----------

        @Test
        void readOnlyPage_StoreBlocked() {
            int perms = PERM_R | PERM_A | PERM_D;
            assertTrue(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
            assertFalse(PermissionCheck.check(perms, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
        }

        @Test
        void readWritePage_FetchBlocked() {
            int perms = PERM_R | PERM_W | PERM_A | PERM_D;
            assertTrue(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
            assertTrue(PermissionCheck.check(perms, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
            assertFalse(PermissionCheck.check(perms, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
        }

        @Test
        void executeOnlyPage_LoadAndStoreDenied() {
            assertFalse(PermissionCheck.check(KERN_X, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
            assertFalse(PermissionCheck.check(KERN_X, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
        }

        @Test
        void noPermissions_AllDenied() {
            int perms = PERM_A | PERM_D; // A and D set but no R/W/X
            assertFalse(PermissionCheck.check(perms, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
            assertFalse(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
            assertFalse(PermissionCheck.check(perms, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
        }

        // ---------- Machine mode ----------

        @Test
        void machineMode_PermissionCheckPassesSupervisorPages() {
            // PermissionCheck itself doesn't special-case M-mode — translation is
            // bypassed in M-mode so this path is never reached in practice.
            // But if called, M-mode is neither USER nor SUPERVISOR, so U-bit checks
            // don't trigger: U=0 pages pass (not USER), U=1 pages also pass (not SUPERVISOR).
            assertTrue(PermissionCheck.check(KERN_RWX, AccessType.LOAD, PRIVILEGE_MODE.MACHINE, 0, 0));
            assertTrue(PermissionCheck.check(USER_RWX, AccessType.LOAD, PRIVILEGE_MODE.MACHINE, 0, 0),
                "M-mode passes U-bit check because it's neither USER nor SUPERVISOR");
        }
    }

    // ================================================================
    //  4. TLB Tests
    // ================================================================
    @Nested
    class TlbTests {
        private TranslationLookasideBuffer tlb;

        @BeforeEach
        void setUp() {
            tlb = new TranslationLookasideBuffer();
        }

        // ---------- Basic translate/insert ----------

        @Test
        void emptyTlbMisses() {
            tlb.translate(0x80001000L, 0);
            assertFalse(tlb.resultHit);
        }

        @Test
        void insertThenHit() {
            tlb.insert(0x80001000L, 0x100, KERN_RWX, false, 0);
            tlb.translate(0x80001000L, 0);
            assertTrue(tlb.resultHit);
            assertEquals(0x00100000L, tlb.resultPA);
            assertFalse(tlb.resultMega);
        }

        @Test
        void pageOffsetPreserved() {
            tlb.insert(0x80001000L, 0x200, KERN_RWX, false, 0);
            tlb.translate(0x80001ABCL, 0);
            assertTrue(tlb.resultHit);
            assertEquals(0x00200ABCL, tlb.resultPA);
        }

        @Test
        void differentVpnMisses() {
            tlb.insert(0x80001000L, 0x100, KERN_RWX, false, 0);
            tlb.translate(0x80002000L, 0);
            assertFalse(tlb.resultHit);
        }

        @Test
        void permsBitsPreserved() {
            int perms = PERM_R | PERM_X | PERM_U | PERM_A;
            tlb.insert(0x80001000L, 0x100, perms, false, 0);
            tlb.translate(0x80001000L, 0);
            assertTrue(tlb.resultHit);
            assertEquals(perms, tlb.resultPerms);
        }

        // ---------- ASID isolation ----------

        @Test
        void differentAsidMisses() {
            tlb.insert(0x80001000L, 0x100, KERN_RWX, false, 1);
            tlb.translate(0x80001000L, 2);
            assertFalse(tlb.resultHit);
        }

        @Test
        void sameAsidHits() {
            tlb.insert(0x80001000L, 0x100, KERN_RWX, false, 7);
            tlb.translate(0x80001000L, 7);
            assertTrue(tlb.resultHit);
        }

        @Test
        void sameVaDifferentAsidsBothCached() {
            tlb.insert(0x80001000L, 0x110, KERN_RWX, false, 0);
            tlb.insert(0x80001000L, 0x220, KERN_RWX, false, 1);

            tlb.translate(0x80001000L, 0);
            assertTrue(tlb.resultHit);
            assertEquals(0x110000L, tlb.resultPA);

            tlb.translate(0x80001000L, 1);
            assertTrue(tlb.resultHit);
            assertEquals(0x220000L, tlb.resultPA);
        }

        // ---------- Global pages ----------

        @Test
        void globalPageMatchesAnyAsid() {
            int perms = KERN_RWX | PERM_G;
            tlb.insert(0x80001000L, 0x100, perms, false, 1);

            tlb.translate(0x80001000L, 99);
            assertTrue(tlb.resultHit, "Global page should match any ASID");
        }

        @Test
        void globalPageSurvivesAsidInvalidation() {
            int perms = KERN_RWX | PERM_G;
            tlb.insert(0x80001000L, 0x100, perms, false, 1);

            tlb.invalidateASID(1);

            tlb.translate(0x80001000L, 1);
            assertTrue(tlb.resultHit, "Global page should survive ASID invalidation");
        }

        @Test
        void globalPageRemovedByAddressInvalidation() {
            int perms = KERN_RWX | PERM_G;
            tlb.insert(0x80001000L, 0x100, perms, false, 1);

            tlb.invalidateAddress(0x80001000L);

            tlb.translate(0x80001000L, 1);
            assertFalse(tlb.resultHit, "Address invalidation should remove global pages");
        }

        @Test
        void globalPageRemovedByFullInvalidation() {
            int perms = KERN_RWX | PERM_G;
            tlb.insert(0x80001000L, 0x100, perms, false, 1);

            tlb.invalidate();

            tlb.translate(0x80001000L, 1);
            assertFalse(tlb.resultHit, "Full invalidation should remove global pages");
        }

        // ---------- Megapages ----------

        @Test
        void megapageTranslation() {
            // PPN[1]=0x201, PPN[0]=0 → PA base = 0x201 << 22 = 0x80400000
            int ppn = 0x201 << 10;
            tlb.insert(0x80400123L, ppn, KERN_RWX, true, 0);

            tlb.translate(0x80400123L, 0);
            assertTrue(tlb.resultHit);
            assertTrue(tlb.resultMega);
            assertEquals(0x80400123L, tlb.resultPA);
        }

        @Test
        void megapageCoversEntire4MBRange() {
            // Identity-mapped megapage at VA 0
            tlb.insert(0x00000000L, 0, KERN_RWX, true, 0);

            for (long offset = 0; offset < 0x400000; offset += 0x10000) {
                tlb.translate(offset, 0);
                assertTrue(tlb.resultHit, "Miss at offset 0x" + Long.toHexString(offset));
                assertTrue(tlb.resultMega);
                assertEquals(offset, tlb.resultPA);
            }
        }

        @Test
        void megapageAndSmallPageCoexist() {
            tlb.insert(0x00000000L, 0, KERN_RWX, true, 0);
            tlb.insert(0x00400000L, 0x110, USER_RWX, false, 0);

            // Megapage region
            tlb.translate(0x00100000L, 0);
            assertTrue(tlb.resultHit);
            assertTrue(tlb.resultMega);

            // Small page region
            tlb.translate(0x00400000L, 0);
            assertTrue(tlb.resultHit);
            assertFalse(tlb.resultMega);
            assertEquals(0x00110000L, tlb.resultPA);
        }

        @Test
        void megapageInvalidateByAddressRemovesEntireMega() {
            tlb.insert(0x00000000L, 0, KERN_RWX, true, 0);

            // Invalidate any address in the megapage range
            tlb.invalidateAddress(0x00200000L);

            tlb.translate(0x00000000L, 0);
            assertFalse(tlb.resultHit, "Megapage should be fully invalidated");
        }

        // ---------- Invalidation ----------

        @Test
        void invalidateAll() {
            tlb.insert(0x80001000L, 0x100, KERN_RWX, false, 0);
            tlb.insert(0x80002000L, 0x200, KERN_RWX, false, 1);

            tlb.invalidate();

            tlb.translate(0x80001000L, 0);
            assertFalse(tlb.resultHit);
            tlb.translate(0x80002000L, 1);
            assertFalse(tlb.resultHit);
        }

        @Test
        void invalidateByAddress_SelectiveRemoval() {
            tlb.insert(0x80001000L, 0x100, KERN_RWX, false, 0);
            tlb.insert(0x80002000L, 0x200, KERN_RWX, false, 0);

            tlb.invalidateAddress(0x80001000L);

            tlb.translate(0x80001000L, 0);
            assertFalse(tlb.resultHit);
            tlb.translate(0x80002000L, 0);
            assertTrue(tlb.resultHit);
        }

        @Test
        void invalidateByAsid_SelectiveRemoval() {
            tlb.insert(0x80001000L, 0x100, KERN_RWX, false, 1);
            tlb.insert(0x80002000L, 0x200, KERN_RWX, false, 2);

            tlb.invalidateASID(1);

            tlb.translate(0x80001000L, 1);
            assertFalse(tlb.resultHit);
            tlb.translate(0x80002000L, 2);
            assertTrue(tlb.resultHit);
        }

        @Test
        void invalidateByAddressAndAsid() {
            tlb.insert(0x80001000L, 0x100, KERN_RWX, false, 1);
            tlb.insert(0x80001000L, 0x200, KERN_RWX, false, 2);

            tlb.invalidateAddressAndASID(0x80001000L, 1);

            tlb.translate(0x80001000L, 1);
            assertFalse(tlb.resultHit, "ASID 1 entry should be removed");
            tlb.translate(0x80001000L, 2);
            assertTrue(tlb.resultHit, "ASID 2 entry should survive");
        }

        @Test
        void invalidateAddressAndAsid_SkipsGlobalPages() {
            int perms = KERN_RWX | PERM_G;
            tlb.insert(0x80001000L, 0x100, perms, false, 1);

            tlb.invalidateAddressAndASID(0x80001000L, 1);

            tlb.translate(0x80001000L, 1);
            assertTrue(tlb.resultHit, "Global page should survive address+ASID invalidation");
        }

        // ---------- LRU eviction ----------

        @Test
        void lruEvictsOldestEntry() {
            // Fill TLB to capacity
            for (int i = 0; i < TLB_SIZE; i++) {
                tlb.insert(0x80000000L + (i * 0x1000L), i, KERN_RWX, false, 0);
            }
            // Touch entry 0 to refresh its LRU timestamp
            tlb.translate(0x80000000L, 0);

            // Insert 129th entry — should evict entry 1 (oldest untouched)
            tlb.insert(0x90000000L, 0xFFF, KERN_RWX, false, 0);

            tlb.translate(0x80000000L, 0);
            assertTrue(tlb.resultHit, "Recently-used entry 0 should survive");
            tlb.translate(0x90000000L, 0);
            assertTrue(tlb.resultHit, "New entry should be present");
            tlb.translate(0x80001000L, 0);
            assertFalse(tlb.resultHit, "LRU entry 1 should be evicted");
        }

        @Test
        void evictionStatistics() {
            for (int i = 0; i < TLB_SIZE; i++) {
                tlb.insert(0x80000000L + (i * 0x1000L), i, KERN_RWX, false, 0);
            }
            assertEquals(0, tlb.getEvictions());

            tlb.insert(0x90000000L, 0xFFF, KERN_RWX, false, 0);
            assertEquals(1, tlb.getEvictions());
        }

        @Test
        void fullCapacity128EntriesAllHit() {
            for (int i = 0; i < TLB_SIZE; i++) {
                tlb.insert(0x80000000L + (i * 0x1000L), 0x1000 + i, KERN_RWX, false, 0);
            }
            assertEquals(128, tlb.getValidEntryCount());

            for (int i = 0; i < TLB_SIZE; i++) {
                tlb.translate(0x80000000L + (i * 0x1000L), 0);
                assertTrue(tlb.resultHit, "Entry " + i + " should hit");
            }
        }

        // ---------- Fast-path cache ----------

        @Test
        void fastPathOnSequentialAccess() {
            tlb.insert(0x00100000L, 0x100, KERN_RWX, false, 0);
            tlb.resetStats();

            for (int i = 0; i < 100; i++) {
                tlb.translate(0x00100000L + (i * 4), 0);
                assertTrue(tlb.resultHit);
            }

            assertTrue(tlb.getFastHits() >= 99, "Expected >=99 fast hits, got " + tlb.getFastHits());
        }

        @Test
        void fastPathInvalidatedByInsert() {
            tlb.insert(0x00100000L, 0x100, KERN_RWX, false, 0);
            tlb.translate(0x00100000L, 0); // prime fast-path

            tlb.insert(0x00200000L, 0x200, KERN_RWX, false, 0); // invalidate fast-path

            tlb.resetStats();
            tlb.translate(0x00100000L, 0);
            assertTrue(tlb.resultHit);
            assertEquals(0, tlb.getFastHits(), "First access after insert should not be fast-path");
        }

        @Test
        void fastPathInvalidatedByFlush() {
            tlb.insert(0x00100000L, 0x100, KERN_RWX, false, 0);
            tlb.translate(0x00100000L, 0); // prime fast-path

            tlb.invalidate();

            tlb.translate(0x00100000L, 0);
            assertFalse(tlb.resultHit, "After full invalidation, should miss");
        }

        // ---------- Statistics ----------

        @Test
        void statisticsAccurate() {
            tlb.insert(0x80001000L, 0x100, KERN_RWX, false, 0);
            assertEquals(1, tlb.getInserts());

            tlb.translate(0x80001000L, 0);
            assertEquals(1, tlb.getHits());

            tlb.translate(0x80003000L, 0);
            assertEquals(1, tlb.getMisses());

            assertEquals(0.5, tlb.getHitRate(), 0.0001);
        }

        @Test
        void dumpFormat() {
            tlb.insert(0x00100000L, 0x100, KERN_RWX, false, 0);
            tlb.insert(0x00000000L, 0, KERN_RWX, true, 0);

            String dump = tlb.dump();
            assertTrue(dump.contains("4KB"), "Should show 4KB entry");
            assertTrue(dump.contains("MEGA"), "Should show megapage entry");
            assertTrue(dump.contains("RWX"), "Should show permissions");
        }
    }

    // ================================================================
    //  5. Translation Enable and checkTlbPermissions via rv32imData
    // ================================================================
    @Nested
    class IntegrationTests {
        rv32imData cpu;
        MSTATUS_CSR mstatus;
        SATP_CSR satp;

        @BeforeEach
        void setup() {
            cpu = new rv32imData(Value.FALSE, 0x80000000L, 1234, false, false,
                rv32imData.CPUState.RUNNING, null);
            mstatus = (MSTATUS_CSR) MMCSR.getCSR(cpu, MMCSR.MSTATUS);
            satp = cpu.getSatp();
        }

        @Test
        void translationDisabledInMMode() {
            satp.MODE.set(1);
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            assertFalse(cpu.isTranslationEnabled(), "M-mode always bare metal");
        }

        @Test
        void translationDisabledWhenSatpModeZero() {
            satp.MODE.set(0);
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
            assertFalse(cpu.isTranslationEnabled());
        }

        @Test
        void translationEnabledInSMode() {
            satp.MODE.set(1);
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
            assertTrue(cpu.isTranslationEnabled());
        }

        @Test
        void translationEnabledInUMode() {
            satp.MODE.set(1);
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.USER);
            assertTrue(cpu.isTranslationEnabled());
        }

        @Test
        void checkTlbPermissions_UsesMstatusSum() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);

            // U-page without SUM — denied
            assertFalse(cpu.checkTlbPermissions(USER_RWX, AccessType.LOAD));

            // Set SUM=1 — allowed
            mstatus.SUM.set(1);
            assertTrue(cpu.checkTlbPermissions(USER_RWX, AccessType.LOAD));
        }

        @Test
        void checkTlbPermissions_UsesMstatusMxr() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);

            // Execute-only page, MXR=0 — load denied
            assertFalse(cpu.checkTlbPermissions(KERN_X, AccessType.LOAD));

            // MXR=1 — load allowed
            mstatus.MXR.set(1);
            assertTrue(cpu.checkTlbPermissions(KERN_X, AccessType.LOAD));
        }

        @Test
        void checkTlbPermissions_AdBitsEnforced() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);

            // A=0 → fault
            int noA = PERM_R | PERM_W | PERM_X | PERM_D;
            assertFalse(cpu.checkTlbPermissions(noA, AccessType.LOAD));

            // A=1, D=0 → store faults
            int noD = PERM_R | PERM_W | PERM_X | PERM_A;
            assertFalse(cpu.checkTlbPermissions(noD, AccessType.STORE));
            // but load succeeds
            assertTrue(cpu.checkTlbPermissions(noD, AccessType.LOAD));
        }

        @Test
        void checkTlbPermissions_PrivilegeModeMatters() {
            // S-mode accessing S-page — allowed
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
            assertTrue(cpu.checkTlbPermissions(KERN_RWX, AccessType.LOAD));

            // U-mode accessing S-page — denied
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.USER);
            assertFalse(cpu.checkTlbPermissions(KERN_RWX, AccessType.LOAD));
        }

        @Test
        void currentAsidFromSatp() {
            satp.ASID.set(42);
            assertEquals(42, cpu.getCurrentASID());
        }
    }

    // ================================================================
    //  6. SFENCE.VMA Instruction Tests
    // ================================================================
    @Nested
    class SfenceVmaTests {
        rv32imData cpu;
        TranslationLookasideBuffer tlb;

        @BeforeEach
        void setup() {
            cpu = new rv32imData(Value.FALSE, 0x80000000L, 1234, false, false,
                rv32imData.CPUState.RUNNING, null);
            MMCSR.getCSR(cpu, MMCSR.MTVEC).write(0x1000);
            tlb = cpu.getTlb();
        }

        /**
         * Encode sfence.vma rs1, rs2.
         * Format: funct7=0x09 | rs2 | rs1 | funct3=0 | rd=0 | opcode=0x73
         */
        static long sfenceVma(int rs1, int rs2) {
            return (0x09L << 25) | ((rs2 & 0x1F) << 20) | ((rs1 & 0x1F) << 15) | 0x73;
        }

        @Test
        void sfenceVma_AllZero_FlushesAll() {
            tlb.insert(0x80001000L, 0x100, KERN_RWX, false, 0);
            tlb.insert(0x80002000L, 0x200, KERN_RWX, false, 1);

            cpu.update(sfenceVma(0, 0), 0, 0, 0);

            tlb.translate(0x80001000L, 0);
            assertFalse(tlb.resultHit);
            tlb.translate(0x80002000L, 1);
            assertFalse(tlb.resultHit);
        }

        @Test
        void sfenceVma_Rs1Only_FlushesAddress() {
            tlb.insert(0x80001000L, 0x100, KERN_RWX, false, 0);
            tlb.insert(0x80002000L, 0x200, KERN_RWX, false, 0);

            // rs1=x1 contains the VA to invalidate
            cpu.setX(1, 0x80001000L);
            cpu.update(sfenceVma(1, 0), 0, 0, 0);

            tlb.translate(0x80001000L, 0);
            assertFalse(tlb.resultHit, "Invalidated address should miss");
            tlb.translate(0x80002000L, 0);
            assertTrue(tlb.resultHit, "Other address should survive");
        }

        @Test
        void sfenceVma_Rs2Only_FlushesAsid() {
            tlb.insert(0x80001000L, 0x100, KERN_RWX, false, 1);
            tlb.insert(0x80002000L, 0x200, KERN_RWX, false, 2);

            // rs2=x2 contains the ASID to invalidate
            cpu.setX(2, 1);
            cpu.update(sfenceVma(0, 2), 0, 0, 0);

            tlb.translate(0x80001000L, 1);
            assertFalse(tlb.resultHit, "ASID 1 should be flushed");
            tlb.translate(0x80002000L, 2);
            assertTrue(tlb.resultHit, "ASID 2 should survive");
        }

        @Test
        void sfenceVma_BothRs_FlushesAddressAndAsid() {
            tlb.insert(0x80001000L, 0x100, KERN_RWX, false, 1);
            tlb.insert(0x80001000L, 0x200, KERN_RWX, false, 2);

            cpu.setX(1, 0x80001000L);
            cpu.setX(2, 1);
            cpu.update(sfenceVma(1, 2), 0, 0, 0);

            tlb.translate(0x80001000L, 1);
            assertFalse(tlb.resultHit, "ASID 1 at that address should be flushed");
            tlb.translate(0x80001000L, 2);
            assertTrue(tlb.resultHit, "ASID 2 at same address should survive");
        }

        @Test
        void sfenceVma_AdvancesPC() {
            long pcBefore = cpu.getPC().get();
            cpu.update(sfenceVma(0, 0), 0, 0, 0);
            assertEquals(pcBefore + 4, cpu.getPC().get(), "SFENCE.VMA should advance PC by 4");
        }
    }

    // ================================================================
    //  7. SRET / MRET Privilege Restoration Tests
    // ================================================================
    @Nested
    class ReturnInstructionTests {
        rv32imData cpu;
        MSTATUS_CSR mstatus;

        @BeforeEach
        void setup() {
            cpu = new rv32imData(Value.FALSE, 0x80000000L, 1234, false, false,
                rv32imData.CPUState.RUNNING, null);
            mstatus = (MSTATUS_CSR) MMCSR.getCSR(cpu, MMCSR.MSTATUS);
            MMCSR.getCSR(cpu, MMCSR.MTVEC).write(0x1000);
        }

        // sret = funct7=0x10 | rs2=0x02(00010) | rs1=0 | funct3=0 | rd=0 | opcode=0x73
        // imm_I = csr = 0x102
        static long sretInstr() {
            return (0x102L << 20) | 0x73;
        }

        // mret = funct7=0x30 | rs2=0x02(00010) | rs1=0 | funct3=0 | rd=0 | opcode=0x73
        // imm_I = csr = 0x302
        static long mretInstr() {
            return (0x302L << 20) | 0x73;
        }

        @Test
        void sret_RestoresPrivilegeFromSpp() {
            // Simulate: trap from U-mode into S-mode
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
            mstatus.SPP.set(0); // SPP=0 means came from User mode
            mstatus.SIE.set(0);
            mstatus.SPIE.set(1);
            cpu.setCSR(SCSR.SEPC.getAddress(), 0x00400000L);

            cpu.update(sretInstr(), 0, 0, 0);

            assertEquals(PRIVILEGE_MODE.USER, cpu.getCurrentPrivilegeMode(),
                "SRET should restore to User mode");
            assertEquals(0x00400000L, cpu.getPC().get(),
                "SRET should set PC to SEPC");
            assertEquals(1, mstatus.SIE.get(), "SIE should be restored from SPIE");
            assertEquals(1, mstatus.SPIE.get(), "SPIE should be set to 1");
            assertEquals(0, mstatus.SPP.get(), "SPP should be set to User");
        }

        @Test
        void sret_RestoresFromSupervisorToSupervisor() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
            mstatus.SPP.set(1); // SPP=1 means came from Supervisor
            cpu.setCSR(SCSR.SEPC.getAddress(), 0x80100000L);

            cpu.update(sretInstr(), 0, 0, 0);

            assertEquals(PRIVILEGE_MODE.SUPERVISOR, cpu.getCurrentPrivilegeMode());
            assertEquals(0x80100000L, cpu.getPC().get());
        }

        @Test
        void mret_RestoresPrivilegeFromMpp() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());
            mstatus.MIE.set(0);
            mstatus.MPIE.set(1);
            MMCSR.getCSR(cpu, MMCSR.MEPC).write(0x80200000L);

            cpu.update(mretInstr(), 0, 0, 0);

            assertEquals(PRIVILEGE_MODE.SUPERVISOR, cpu.getCurrentPrivilegeMode(),
                "MRET should restore to Supervisor mode");
            assertEquals(0x80200000L, cpu.getPC().get(),
                "MRET should set PC to MEPC");
            assertEquals(1, mstatus.MIE.get(), "MIE should be restored from MPIE");
            assertEquals(1, mstatus.MPIE.get(), "MPIE should be set to 1");
            assertEquals(0, mstatus.MPP.get(), "MPP should be set to User");
        }

        @Test
        void mret_DoesNotClearMipBits() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());
            mstatus.MIE.set(0); // disable interrupts so they don't fire during MRET
            MMCSR.getCSR(cpu, MMCSR.MEPC).write(0x80200000L);

            // Pass timer=1 and external=1 as input pins so update() sets MIP bits
            // Signature: update(dataIn, timerInterruptRequest, externalInterruptRequest, waitRequest)
            cpu.update(mretInstr(), 1, 1, 0);

            // MIP bits should reflect the hardware input state, not be cleared by MRET
            MIP_CSR mip = (MIP_CSR) MMCSR.getCSR(cpu, MMCSR.MIP);
            assertEquals(1, mip.MTIP.get(), "MTIP should reflect timer input, not cleared by MRET");
            assertEquals(1, mip.MEIP.get(), "MEIP should reflect external input, not cleared by MRET");
        }
    }

    // ================================================================
    //  8. Page Fault Trap Delegation and stval/mtval Tests
    // ================================================================
    @Nested
    class PageFaultTrapTests {
        rv32imData cpu;
        MSTATUS_CSR mstatus;
        MCAUSE_CSR mcause;

        @BeforeEach
        void setup() {
            cpu = new rv32imData(Value.FALSE, 0x80000000L, 1234, false, false,
                rv32imData.CPUState.RUNNING, null);
            mstatus = (MSTATUS_CSR) MMCSR.getCSR(cpu, MMCSR.MSTATUS);
            mcause = (MCAUSE_CSR) MMCSR.getCSR(cpu, MMCSR.MCAUSE);
            MMCSR.getCSR(cpu, MMCSR.MTVEC).write(0x1000);
        }

        @Test
        void pageFault_SetsMtvalToFaultingVA_Load() {
            cpu.handlePageFault(0xDEADBEEFL, AccessType.LOAD);

            assertEquals(13, mcause.EXCEPTION_CODE.get(), "Load page fault = 13");
            assertEquals(0, mcause.INTERRUPT.get(), "Should be exception, not interrupt");
            assertEquals(0xDEADBEEFL, MMCSR.getValue(cpu, MMCSR.MTVAL),
                "mtval should be the faulting VA");
        }

        @Test
        void pageFault_SetsMtvalToFaultingVA_Store() {
            cpu.handlePageFault(0xCAFEBABEL, AccessType.STORE);

            assertEquals(15, mcause.EXCEPTION_CODE.get(), "Store page fault = 15");
            assertEquals(0xCAFEBABEL, MMCSR.getValue(cpu, MMCSR.MTVAL));
        }

        @Test
        void pageFault_SetsMtvalToFaultingVA_Fetch() {
            cpu.handlePageFault(0x00400000L, AccessType.FETCH);

            assertEquals(12, mcause.EXCEPTION_CODE.get(), "Instruction page fault = 12");
            assertEquals(0x00400000L, MMCSR.getValue(cpu, MMCSR.MTVAL));
        }

        @Test
        void pageFault_DelegatedToSMode() {
            // Set up delegation: delegate instruction/load/store page faults to S-mode
            MEDELEG_CSR medeleg = (MEDELEG_CSR) MMCSR.getCSR(cpu, MMCSR.MEDELEG);
            medeleg.write((1L << 12) | (1L << 13) | (1L << 15)); // delegate all page faults

            // Configure S-mode trap handler
            STVEC_CSR stvec = (STVEC_CSR) cpu.getCSR(SCSR.STVEC.getAddress());
            stvec.write(0x80100000L);

            // Execute in S-mode
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);

            cpu.handlePageFault(0xBAADF00DL, AccessType.LOAD);

            // Should be delegated to S-mode handler
            SCAUSE_CSR scause = (SCAUSE_CSR) cpu.getCSR(SCSR.SCAUSE.getAddress());
            assertEquals(13, scause.EXCEPTION_CODE.get(), "SCAUSE should have load page fault");
            assertEquals(0xBAADF00DL, cpu.getCSR(SCSR.STVAL.getAddress()).read(),
                "stval should be faulting VA");
            assertEquals(0x80100000L, cpu.getPC().get(),
                "PC should jump to STVEC");
            assertEquals(PRIVILEGE_MODE.SUPERVISOR, cpu.getCurrentPrivilegeMode(),
                "Should remain in S-mode");
        }

        @Test
        void pageFault_NotDelegated_GoesToMMode() {
            // medeleg = 0 → no delegation
            MEDELEG_CSR medeleg = (MEDELEG_CSR) MMCSR.getCSR(cpu, MMCSR.MEDELEG);
            medeleg.write(0);

            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
            cpu.handlePageFault(0x12345678L, AccessType.STORE);

            assertEquals(15, mcause.EXCEPTION_CODE.get());
            assertEquals(0x12345678L, MMCSR.getValue(cpu, MMCSR.MTVAL));
            assertEquals(0x1000L, cpu.getPC().get(), "PC should jump to MTVEC");
            assertEquals(PRIVILEGE_MODE.MACHINE, cpu.getCurrentPrivilegeMode(),
                "Should trap to M-mode");
        }

        @Test
        void pageFault_MMode_AlwaysHandledInMMode() {
            // Even with delegation set, M-mode traps stay in M-mode
            MEDELEG_CSR medeleg = (MEDELEG_CSR) MMCSR.getCSR(cpu, MMCSR.MEDELEG);
            medeleg.write((1L << 13));

            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            cpu.handlePageFault(0xAAAAAAAAL, AccessType.LOAD);

            assertEquals(PRIVILEGE_MODE.MACHINE, cpu.getCurrentPrivilegeMode(),
                "M-mode page faults cannot be delegated");
        }
    }

    // ================================================================
    //  9. Edge Cases and Boundary Conditions
    // ================================================================
    @Nested
    class EdgeCaseTests {

        @Test
        void permissionCheck_AllBitsZero_FaultsEverything() {
            for (AccessType t : AccessType.values()) {
                assertFalse(PermissionCheck.check(0, t, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
                assertFalse(PermissionCheck.check(0, t, PRIVILEGE_MODE.USER, 0, 0));
            }
        }

        @Test
        void permissionCheck_OnlyABitSet_NoRwx() {
            int perms = PERM_A;
            for (AccessType t : AccessType.values()) {
                assertFalse(PermissionCheck.check(perms, t, PRIVILEGE_MODE.SUPERVISOR, 0, 0),
                    "A-only should deny " + t);
            }
        }

        @Test
        void permissionCheck_GlobalBitDoesNotAffectPermissions() {
            // G bit is for TLB ASID matching, not permission checks
            int withG = KERN_RWX | PERM_G;
            int withoutG = KERN_RWX;
            for (AccessType t : AccessType.values()) {
                assertEquals(
                    PermissionCheck.check(withG, t, PRIVILEGE_MODE.SUPERVISOR, 0, 0),
                    PermissionCheck.check(withoutG, t, PRIVILEGE_MODE.SUPERVISOR, 0, 0),
                    "G bit should not affect permission for " + t);
            }
        }

        @Test
        void tlb_MaxAsidValue() {
            TranslationLookasideBuffer tlb = new TranslationLookasideBuffer();
            tlb.insert(0x80001000L, 0x100, KERN_RWX, false, 511);
            tlb.translate(0x80001000L, 511);
            assertTrue(tlb.resultHit, "Max ASID (511) should work");
            tlb.translate(0x80001000L, 510);
            assertFalse(tlb.resultHit, "Different ASID should miss");
        }

        @Test
        void tlb_ZeroVirtualAddress() {
            TranslationLookasideBuffer tlb = new TranslationLookasideBuffer();
            tlb.insert(0x00000000L, 0x100, KERN_RWX, false, 0);
            tlb.translate(0x00000000L, 0);
            assertTrue(tlb.resultHit);
            assertEquals(0x00100000L, tlb.resultPA);
        }

        @Test
        void tlb_MaxVirtualAddress() {
            TranslationLookasideBuffer tlb = new TranslationLookasideBuffer();
            // VPN for 0xFFFFF000 = 0xFFFFF, PPN = 0x3FFFFF
            // PA = 0x3FFFFF << 12 | 0x000 = 0x3FFFFF000 (34-bit PA per SV32 spec)
            tlb.insert(0xFFFFF000L, 0x3FFFFF, KERN_RWX, false, 0);
            tlb.translate(0xFFFFF000L, 0);
            assertTrue(tlb.resultHit);
            assertEquals(0x3FFFFF000L, tlb.resultPA, "SV32 34-bit physical address");
        }

        @Test
        void tlb_HighPhysicalAddress() {
            TranslationLookasideBuffer tlb = new TranslationLookasideBuffer();
            // PPN = 0x3FFFFF → PA base = 0x3FFFFF << 12 = 0x3FFFFF000 (34-bit PA per SV32 spec)
            tlb.insert(0x00001000L, 0x3FFFFF, KERN_RWX, false, 0);
            tlb.translate(0x00001000L, 0);
            assertTrue(tlb.resultHit);
            assertEquals(0x3FFFFF000L, tlb.resultPA, "SV32 supports 34-bit physical addresses");
        }

        @Test
        void satp_ZeroValue() {
            SATP_CSR satp = new SATP_CSR(0);
            assertEquals(0, satp.MODE.get());
            assertEquals(0, satp.ASID.get());
            assertEquals(0, satp.PPN.get());
            assertEquals(0, satp.getRootPageTableAddress());
            assertFalse(satp.isSV32Enabled());
        }

        @Test
        void permissionCheck_StoreRequiresBothAandD() {
            // A=1, D=1, W=1 → store allowed
            int both = PERM_W | PERM_A | PERM_D;
            assertTrue(PermissionCheck.check(both, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));

            // A=0, D=1, W=1 → fault (A required)
            int noA = PERM_W | PERM_D;
            assertFalse(PermissionCheck.check(noA, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));

            // A=1, D=0, W=1 → fault (D required for store)
            int noD = PERM_W | PERM_A;
            assertFalse(PermissionCheck.check(noD, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));

            // A=0, D=0, W=1 → fault
            int neither = PERM_W;
            assertFalse(PermissionCheck.check(neither, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
        }

        @Test
        void demandPagingScenario() {
            // OS marks page as valid but A=0 → first access faults → OS sets A=1, retries
            int freshPage = PERM_R | PERM_W | PERM_X;
            assertFalse(PermissionCheck.check(freshPage, AccessType.LOAD, PRIVILEGE_MODE.USER, 0, 0),
                "First access to fresh page should fault (A=0)");

            // After OS sets A bit
            int touched = freshPage | PERM_A | PERM_U;
            assertTrue(PermissionCheck.check(touched, AccessType.LOAD, PRIVILEGE_MODE.USER, 0, 0),
                "After OS sets A bit, load should succeed");
        }

        @Test
        void copyOnWriteScenario() {
            // OS maps page as R only (no W, no D) → write faults → OS does COW
            int readOnly = PERM_R | PERM_A | PERM_U;
            assertTrue(PermissionCheck.check(readOnly, AccessType.LOAD, PRIVILEGE_MODE.USER, 0, 0),
                "Read should succeed on RO page");
            assertFalse(PermissionCheck.check(readOnly, AccessType.STORE, PRIVILEGE_MODE.USER, 0, 0),
                "Write should fault on RO page (triggers COW)");

            // After COW: new page with R, W, A, D
            int writable = PERM_R | PERM_W | PERM_A | PERM_D | PERM_U;
            assertTrue(PermissionCheck.check(writable, AccessType.STORE, PRIVILEGE_MODE.USER, 0, 0),
                "Write to new COW page should succeed");
        }

        @Test
        void kernelAccessingUserMemory_Scenario() {
            // Kernel needs to copy data from user buffer
            // Without SUM: denied
            assertFalse(PermissionCheck.check(USER_RW, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
            // With SUM: allowed
            assertTrue(PermissionCheck.check(USER_RW, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 1, 0));
            assertTrue(PermissionCheck.check(USER_RW, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 1, 0));
            // But never for instruction fetch
            assertFalse(PermissionCheck.check(USER_RX, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 1, 0));
        }
    }
}
