package com.cburch.logisim.riscv;

import com.cburch.logisim.data.Value;
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
 * Comprehensive tests for MPRV (Modify PRiVilege) functionality.
 *
 * Per RISC-V Privileged Spec §3.1.6.3:
 * - When mstatus.MPRV=1, load and store addresses are translated and protected
 *   as though the current privilege mode were set to mstatus.MPP.
 * - Instruction address-translation and protection are unaffected by MPRV.
 * - MPRV is only effective in M-mode. In S-mode and U-mode, MPRV is ignored.
 * - MRET clears MPRV when MPP != M-mode.
 *
 * Test categories:
 * 1. MPRV bitfield in MSTATUS
 * 2. Effective privilege mode computation
 * 3. Translation enable for load/store vs fetch
 * 4. Permission checks with effective privilege
 * 5. MRET clearing of MPRV
 * 6. OS-level scenarios (copy_from_user, copy_to_user)
 */
class MprvTest {

    static final int KERN_RWX = PERM_R | PERM_W | PERM_X | PERM_A | PERM_D;
    static final int USER_RWX = KERN_RWX | PERM_U;
    static final int USER_RW  = PERM_R | PERM_W | PERM_A | PERM_D | PERM_U;

    // ================================================================
    //  1. MPRV Bitfield Tests
    // ================================================================
    @Nested
    class MprvBitfieldTests {

        @Test
        void mprvFieldIsBit17() {
            MSTATUS_CSR mstatus = new MSTATUS_CSR(0);
            assertEquals(0, mstatus.MPRV.get());
            mstatus.MPRV.set(1);
            assertEquals(1, mstatus.MPRV.get());
            assertTrue((mstatus.read() & (1L << 17)) != 0, "MPRV should be bit 17");
        }

        @Test
        void mprvDoesNotAffectOtherFields() {
            MSTATUS_CSR mstatus = new MSTATUS_CSR(0);
            mstatus.MIE.set(1);
            mstatus.SUM.set(1);
            mstatus.MXR.set(1);
            mstatus.MPRV.set(1);
            assertEquals(1, mstatus.MIE.get());
            assertEquals(1, mstatus.SUM.get());
            assertEquals(1, mstatus.MXR.get());
            assertEquals(1, mstatus.MPRV.get());
        }

        @Test
        void mprvClearAndSetRoundTrip() {
            MSTATUS_CSR mstatus = new MSTATUS_CSR(0);
            mstatus.MPRV.set(1);
            assertEquals(1, mstatus.MPRV.get());
            mstatus.MPRV.set(0);
            assertEquals(0, mstatus.MPRV.get());
        }

        @Test
        void mprvNotExposedThroughSstatus() {
            MSTATUS_CSR mstatus = new MSTATUS_CSR(0);
            SSTATUS_CSR sstatus = new SSTATUS_CSR(mstatus);

            mstatus.MPRV.set(1);
            assertEquals(0, sstatus.read() & (1L << 17),
                "SSTATUS should not expose MPRV (M-mode only)");
        }

        @Test
        void sstatusWriteDoesNotClobberMprv() {
            MSTATUS_CSR mstatus = new MSTATUS_CSR(0);
            SSTATUS_CSR sstatus = new SSTATUS_CSR(mstatus);

            mstatus.MPRV.set(1);
            sstatus.write(0); // write 0 through sstatus — MPRV should survive
            assertEquals(1, mstatus.MPRV.get(), "MPRV must survive sstatus write");
        }
    }

    // ================================================================
    //  2. Effective Privilege Mode Tests
    // ================================================================
    @Nested
    class EffectivePrivilegeTests {
        rv32imData cpu;
        MSTATUS_CSR mstatus;

        @BeforeEach
        void setup() {
            cpu = new rv32imData(Value.FALSE, 0x80000000L, 1234, false, false,
                rv32imData.CPUState.RUNNING, null);
            mstatus = (MSTATUS_CSR) MMCSR.getCSR(cpu, MMCSR.MSTATUS);
        }

        @Test
        void mMode_MprvOff_EffectiveIsMachine() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(0);
            assertEquals(PRIVILEGE_MODE.MACHINE, cpu.getEffectivePrivilegeForLoadStore());
        }

        @Test
        void mMode_MprvOn_MppSupervisor_EffectiveIsSupervisor() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());
            assertEquals(PRIVILEGE_MODE.SUPERVISOR, cpu.getEffectivePrivilegeForLoadStore());
        }

        @Test
        void mMode_MprvOn_MppUser_EffectiveIsUser() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.USER.getValue());
            assertEquals(PRIVILEGE_MODE.USER, cpu.getEffectivePrivilegeForLoadStore());
        }

        @Test
        void mMode_MprvOn_MppMachine_EffectiveIsMachine() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.MACHINE.getValue());
            assertEquals(PRIVILEGE_MODE.MACHINE, cpu.getEffectivePrivilegeForLoadStore());
        }

        @Test
        void sMode_MprvIgnored() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.USER.getValue());
            assertEquals(PRIVILEGE_MODE.SUPERVISOR, cpu.getEffectivePrivilegeForLoadStore(),
                "MPRV only applies in M-mode, should be ignored in S-mode");
        }

        @Test
        void uMode_MprvIgnored() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.USER);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());
            assertEquals(PRIVILEGE_MODE.USER, cpu.getEffectivePrivilegeForLoadStore(),
                "MPRV only applies in M-mode, should be ignored in U-mode");
        }
    }

    // ================================================================
    //  3. Translation Enable Tests (Load/Store vs Fetch)
    // ================================================================
    @Nested
    class TranslationEnableTests {
        rv32imData cpu;
        MSTATUS_CSR mstatus;
        SATP_CSR satp;

        @BeforeEach
        void setup() {
            cpu = new rv32imData(Value.FALSE, 0x80000000L, 1234, false, false,
                rv32imData.CPUState.RUNNING, null);
            mstatus = (MSTATUS_CSR) MMCSR.getCSR(cpu, MMCSR.MSTATUS);
            satp = cpu.getSatp();
            satp.MODE.set(1); // Enable SV32
        }

        @Test
        void mMode_MprvOff_LoadStoreNotTranslated() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(0);
            assertFalse(cpu.isTranslationEnabledForLoadStore(),
                "Without MPRV, M-mode loads/stores use physical addresses");
        }

        @Test
        void mMode_MprvOn_MppSupervisor_LoadStoreTranslated() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());
            assertTrue(cpu.isTranslationEnabledForLoadStore(),
                "MPRV=1, MPP=S → loads/stores should be translated");
        }

        @Test
        void mMode_MprvOn_MppUser_LoadStoreTranslated() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.USER.getValue());
            assertTrue(cpu.isTranslationEnabledForLoadStore(),
                "MPRV=1, MPP=U → loads/stores should be translated");
        }

        @Test
        void mMode_MprvOn_MppMachine_LoadStoreNotTranslated() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.MACHINE.getValue());
            assertFalse(cpu.isTranslationEnabledForLoadStore(),
                "MPRV=1, MPP=M → M-mode doesn't translate");
        }

        @Test
        void mMode_MprvOn_FetchStillNotTranslated() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());
            assertFalse(cpu.isTranslationEnabled(),
                "MPRV does NOT affect instruction fetch — fetch always uses actual privilege");
        }

        @Test
        void mMode_MprvOn_SatpDisabled_LoadStoreNotTranslated() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());
            satp.MODE.set(0); // SV32 disabled
            assertFalse(cpu.isTranslationEnabledForLoadStore(),
                "Even with MPRV, SATP.MODE must be enabled for translation");
        }

        @Test
        void sMode_LoadStoreUsesNormalTranslation() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.SUPERVISOR);
            assertTrue(cpu.isTranslationEnabledForLoadStore(),
                "S-mode with SV32 enabled should translate load/store normally");
            assertTrue(cpu.isTranslationEnabled(),
                "S-mode with SV32 enabled should translate fetch normally");
        }
    }

    // ================================================================
    //  4. Permission Checks with Effective Privilege
    // ================================================================
    @Nested
    class PermissionWithMprvTests {
        rv32imData cpu;
        MSTATUS_CSR mstatus;

        @BeforeEach
        void setup() {
            cpu = new rv32imData(Value.FALSE, 0x80000000L, 1234, false, false,
                rv32imData.CPUState.RUNNING, null);
            mstatus = (MSTATUS_CSR) MMCSR.getCSR(cpu, MMCSR.MSTATUS);
        }

        @Test
        void mMode_MprvMppUser_CanAccessUserPages() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.USER.getValue());

            assertTrue(cpu.checkTlbPermissionsForLoadStore(USER_RWX, AccessType.LOAD),
                "M+MPRV+MPP=U should access U-pages");
        }

        @Test
        void mMode_MprvMppUser_CannotAccessSupervisorPages() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.USER.getValue());

            assertFalse(cpu.checkTlbPermissionsForLoadStore(KERN_RWX, AccessType.LOAD),
                "M+MPRV+MPP=U should NOT access S-pages (U-mode can't read S-pages)");
        }

        @Test
        void mMode_MprvMppSupervisor_CanAccessSupervisorPages() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());

            assertTrue(cpu.checkTlbPermissionsForLoadStore(KERN_RWX, AccessType.LOAD),
                "M+MPRV+MPP=S should access S-pages");
        }

        @Test
        void mMode_MprvMppSupervisor_UserPageDenied_WithoutSum() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());
            mstatus.SUM.set(0);

            assertFalse(cpu.checkTlbPermissionsForLoadStore(USER_RWX, AccessType.LOAD),
                "M+MPRV+MPP=S, SUM=0 → U-page denied");
        }

        @Test
        void mMode_MprvMppSupervisor_UserPageAllowed_WithSum() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());
            mstatus.SUM.set(1);

            assertTrue(cpu.checkTlbPermissionsForLoadStore(USER_RWX, AccessType.LOAD),
                "M+MPRV+MPP=S, SUM=1 → U-page allowed for load/store");
        }

        @Test
        void mMode_MprvMppSupervisor_MxrApplies() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());

            int executeOnly = PERM_X | PERM_A;
            assertFalse(cpu.checkTlbPermissionsForLoadStore(executeOnly, AccessType.LOAD),
                "MXR=0 → execute-only denies load");

            mstatus.MXR.set(1);
            assertTrue(cpu.checkTlbPermissionsForLoadStore(executeOnly, AccessType.LOAD),
                "MXR=1 → execute-only allows load");
        }

        @Test
        void mMode_MprvOff_PermissionCheckUsesActualMode() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(0);
            mstatus.MPP.set(PRIVILEGE_MODE.USER.getValue());

            // Without MPRV, M-mode: PermissionCheck uses MACHINE mode
            // M-mode is neither USER nor SUPERVISOR, so U-bit checks don't trigger
            assertTrue(cpu.checkTlbPermissionsForLoadStore(USER_RWX, AccessType.LOAD),
                "M-mode without MPRV bypasses U-bit checks");
            assertTrue(cpu.checkTlbPermissionsForLoadStore(KERN_RWX, AccessType.LOAD),
                "M-mode without MPRV bypasses U-bit checks");
        }

        @Test
        void fetchPermissionCheck_IgnoresMprv() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.USER.getValue());

            // checkTlbPermissions (for fetch) uses actual M-mode, not effective
            assertTrue(cpu.checkTlbPermissions(KERN_RWX, AccessType.FETCH),
                "Fetch perm check uses actual M-mode, not MPRV effective mode");
        }

        @Test
        void adBitsStillEnforcedWithMprv() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());

            int noA = PERM_R | PERM_W | PERM_D;
            assertFalse(cpu.checkTlbPermissionsForLoadStore(noA, AccessType.LOAD),
                "A=0 should fault even with MPRV");

            int noD = PERM_R | PERM_W | PERM_A;
            assertFalse(cpu.checkTlbPermissionsForLoadStore(noD, AccessType.STORE),
                "D=0 should fault store even with MPRV");
        }
    }

    // ================================================================
    //  5. MRET MPRV Clearing Tests
    // ================================================================
    @Nested
    class MretMprvClearingTests {
        rv32imData cpu;
        MSTATUS_CSR mstatus;

        // mret = funct7=0x30 | rs2=0x02(00010) | rs1=0 | funct3=0 | rd=0 | opcode=0x73
        static long mretInstr() {
            return (0x302L << 20) | 0x73;
        }

        @BeforeEach
        void setup() {
            cpu = new rv32imData(Value.FALSE, 0x80000000L, 1234, false, false,
                rv32imData.CPUState.RUNNING, null);
            mstatus = (MSTATUS_CSR) MMCSR.getCSR(cpu, MMCSR.MSTATUS);
            MMCSR.getCSR(cpu, MMCSR.MTVEC).write(0x1000);
            MMCSR.getCSR(cpu, MMCSR.MEPC).write(0x80200000L);
        }

        @Test
        void mret_MppSupervisor_ClearsMprv() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());

            cpu.update(mretInstr(), 0, 0, 0);

            assertEquals(0, mstatus.MPRV.get(),
                "MRET should clear MPRV when returning to S-mode");
            assertEquals(PRIVILEGE_MODE.SUPERVISOR, cpu.getCurrentPrivilegeMode());
        }

        @Test
        void mret_MppUser_ClearsMprv() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.USER.getValue());

            cpu.update(mretInstr(), 0, 0, 0);

            assertEquals(0, mstatus.MPRV.get(),
                "MRET should clear MPRV when returning to U-mode");
            assertEquals(PRIVILEGE_MODE.USER, cpu.getCurrentPrivilegeMode());
        }

        @Test
        void mret_MppMachine_PreservesMprv() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.MACHINE.getValue());

            cpu.update(mretInstr(), 0, 0, 0);

            assertEquals(1, mstatus.MPRV.get(),
                "MRET should preserve MPRV when returning to M-mode");
            assertEquals(PRIVILEGE_MODE.MACHINE, cpu.getCurrentPrivilegeMode());
        }

        @Test
        void mret_MprvAlreadyZero_StaysZero() {
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(0);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());

            cpu.update(mretInstr(), 0, 0, 0);

            assertEquals(0, mstatus.MPRV.get(), "MPRV was 0, should remain 0");
        }
    }

    // ================================================================
    //  6. OS-Level Scenarios
    // ================================================================
    @Nested
    class OsScenarioTests {
        rv32imData cpu;
        MSTATUS_CSR mstatus;
        SATP_CSR satp;

        @BeforeEach
        void setup() {
            cpu = new rv32imData(Value.FALSE, 0x80000000L, 1234, false, false,
                rv32imData.CPUState.RUNNING, null);
            mstatus = (MSTATUS_CSR) MMCSR.getCSR(cpu, MMCSR.MSTATUS);
            satp = cpu.getSatp();
            satp.MODE.set(1); // SV32 enabled
        }

        @Test
        void copyFromUser_MprvEnablesTranslation() {
            // Scenario: M-mode trap handler needs to read from user virtual address
            // Set MPRV=1, MPP=U to access user memory with translation
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.USER.getValue());

            // Translation should be enabled for loads
            assertTrue(cpu.isTranslationEnabledForLoadStore());
            // But NOT for instruction fetch
            assertFalse(cpu.isTranslationEnabled());

            // Can access user pages
            assertTrue(cpu.checkTlbPermissionsForLoadStore(USER_RW, AccessType.LOAD));
            assertTrue(cpu.checkTlbPermissionsForLoadStore(USER_RW, AccessType.STORE));

            // Cannot access supervisor pages (effective privilege is USER)
            assertFalse(cpu.checkTlbPermissionsForLoadStore(KERN_RWX, AccessType.LOAD));
        }

        @Test
        void copyFromUser_MprvWithMppSupervisor_SumNeeded() {
            // Scenario: S-mode kernel trapped to M-mode, wants to read user buffer
            // MPP=S means we act as S-mode — need SUM=1 for user pages
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());
            mstatus.SUM.set(0);

            // With SUM=0, S-mode effective privilege can't access U-pages
            assertFalse(cpu.checkTlbPermissionsForLoadStore(USER_RW, AccessType.LOAD));

            // Set SUM=1
            mstatus.SUM.set(1);
            assertTrue(cpu.checkTlbPermissionsForLoadStore(USER_RW, AccessType.LOAD));
        }

        @Test
        void trapHandlerFetchFromPhysical_DataFromVirtual() {
            // Core MPRV contract: M-mode trap handler code runs from physical addresses
            // but can access translated load/store addresses
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());

            assertFalse(cpu.isTranslationEnabled(), "Fetch from physical (M-mode)");
            assertTrue(cpu.isTranslationEnabledForLoadStore(), "Load/store from virtual (S-mode)");
        }

        @Test
        void mretAfterCopyFromUser_DisablesMprv() {
            // After trap handler finishes copy_from_user, MRET returns to S-mode
            // MPRV should be automatically cleared
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(1);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());

            // Before MRET: MPRV is active
            assertTrue(cpu.isTranslationEnabledForLoadStore());

            // MRET
            MMCSR.getCSR(cpu, MMCSR.MEPC).write(0x80100000L);
            cpu.update(MretMprvClearingTests.mretInstr(), 0, 0, 0);

            // After MRET: now in S-mode, MPRV cleared
            assertEquals(PRIVILEGE_MODE.SUPERVISOR, cpu.getCurrentPrivilegeMode());
            assertEquals(0, mstatus.MPRV.get());
            // Translation still works because we're in S-mode with SV32 enabled
            assertTrue(cpu.isTranslationEnabled());
            assertTrue(cpu.isTranslationEnabledForLoadStore());
        }

        @Test
        void mMode_WithoutMprv_PhysicalAccessOnly() {
            // Normal M-mode: no translation for any access
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPRV.set(0);

            assertFalse(cpu.isTranslationEnabled());
            assertFalse(cpu.isTranslationEnabledForLoadStore());
        }

        @Test
        void mMode_MprvToggle_TranslationFollows() {
            // Toggle MPRV on and off, translation should follow
            cpu.setCurrentPrivilegeMode(PRIVILEGE_MODE.MACHINE);
            mstatus.MPP.set(PRIVILEGE_MODE.SUPERVISOR.getValue());

            mstatus.MPRV.set(0);
            assertFalse(cpu.isTranslationEnabledForLoadStore());

            mstatus.MPRV.set(1);
            assertTrue(cpu.isTranslationEnabledForLoadStore());

            mstatus.MPRV.set(0);
            assertFalse(cpu.isTranslationEnabledForLoadStore());
        }
    }
}
