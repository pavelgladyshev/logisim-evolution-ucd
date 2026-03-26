package com.cburch.logisim.riscv;

import com.cburch.logisim.riscv.cpu.PermissionCheck;
import com.cburch.logisim.riscv.cpu.TranslationLookasideBuffer;
import com.cburch.logisim.riscv.cpu.TranslationLookasideBuffer.AccessType;
import com.cburch.logisim.riscv.cpu.csrs.PRIVILEGE_MODE;
import org.junit.jupiter.api.Test;

import static com.cburch.logisim.riscv.cpu.TranslationLookasideBuffer.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SV32 permission checks per RISC-V Privileged Spec Section 4.3.2, step 6.
 */
class PermissionCheckTest {

    // ========== A/D Bit Tests ==========

    @Test
    void testAccessedBitNotSet_FetchFaults() {
        // A=0, D=1 — should fault for any access
        int perms = PERM_R | PERM_W | PERM_X | PERM_D;
        assertFalse(PermissionCheck.check(perms, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
    }

    @Test
    void testAccessedBitNotSet_LoadFaults() {
        int perms = PERM_R | PERM_W | PERM_X | PERM_D;
        assertFalse(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
    }

    @Test
    void testAccessedBitNotSet_StoreFaults() {
        int perms = PERM_R | PERM_W | PERM_X | PERM_D;
        assertFalse(PermissionCheck.check(perms, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
    }

    @Test
    void testDirtyBitNotSet_StoreFaults() {
        // A=1, D=0 — should fault for STORE but not LOAD
        int perms = PERM_R | PERM_W | PERM_X | PERM_A;
        assertFalse(PermissionCheck.check(perms, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
    }

    @Test
    void testDirtyBitNotSet_LoadSucceeds() {
        // A=1, D=0 — LOAD should succeed (D bit only required for stores)
        int perms = PERM_R | PERM_X | PERM_A;
        assertTrue(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
    }

    @Test
    void testDirtyBitNotSet_FetchSucceeds() {
        // A=1, D=0 — FETCH should succeed (D bit only required for stores)
        int perms = PERM_X | PERM_A;
        assertTrue(PermissionCheck.check(perms, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
    }

    @Test
    void testAccessedAndDirtySet_StoreSucceeds() {
        int perms = PERM_R | PERM_W | PERM_A | PERM_D;
        assertTrue(PermissionCheck.check(perms, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
    }

    // ========== U-bit Privilege Tests ==========

    @Test
    void testSupervisorPage_DeniedInUserMode() {
        // U=0 page, U-mode → fault
        int perms = PERM_R | PERM_W | PERM_X | PERM_A | PERM_D;
        assertFalse(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.USER, 0, 0));
    }

    @Test
    void testSupervisorPage_AllowedInSupervisorMode() {
        // U=0 page, S-mode → allowed
        int perms = PERM_R | PERM_W | PERM_X | PERM_A | PERM_D;
        assertTrue(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
    }

    @Test
    void testUserPage_AllowedInUserMode() {
        // U=1 page, U-mode → allowed
        int perms = PERM_R | PERM_W | PERM_X | PERM_U | PERM_A | PERM_D;
        assertTrue(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.USER, 0, 0));
    }

    @Test
    void testUserPage_DeniedInSupervisorMode_SumOff() {
        // U=1 page, S-mode, SUM=0 → fault
        int perms = PERM_R | PERM_W | PERM_X | PERM_U | PERM_A | PERM_D;
        assertFalse(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
    }

    @Test
    void testUserPage_AllowedInSupervisorMode_SumOn() {
        // U=1 page, S-mode, SUM=1 → allowed for loads/stores
        int perms = PERM_R | PERM_W | PERM_X | PERM_U | PERM_A | PERM_D;
        assertTrue(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 1, 0));
        assertTrue(PermissionCheck.check(perms, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 1, 0));
    }

    @Test
    void testUserPage_FetchDeniedInSMode_EvenWithSum() {
        // U=1 page, S-mode, SUM=1, FETCH → always fault (SUM doesn't affect fetches)
        int perms = PERM_R | PERM_W | PERM_X | PERM_U | PERM_A | PERM_D;
        assertFalse(PermissionCheck.check(perms, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 1, 0));
    }

    @Test
    void testUserPage_FetchAllowedInUserMode() {
        // U=1 page, U-mode, FETCH → allowed
        int perms = PERM_X | PERM_U | PERM_A;
        assertTrue(PermissionCheck.check(perms, AccessType.FETCH, PRIVILEGE_MODE.USER, 0, 0));
    }

    // ========== MXR Tests ==========

    @Test
    void testMxrDisabled_ExecuteOnlyPageDeniesLoad() {
        // X=1, R=0, MXR=0 → load denied
        int perms = PERM_X | PERM_A;
        assertFalse(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
    }

    @Test
    void testMxrEnabled_ExecuteOnlyPageAllowsLoad() {
        // X=1, R=0, MXR=1 → load allowed
        int perms = PERM_X | PERM_A;
        assertTrue(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 1));
    }

    @Test
    void testMxrEnabled_NonExecutablePageStillDeniesLoad() {
        // X=0, R=0, MXR=1 → load denied (MXR only helps if X=1)
        int perms = PERM_W | PERM_A | PERM_D;
        assertFalse(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 1));
    }

    @Test
    void testMxrDoesNotAffectFetch() {
        // MXR should not change fetch behavior — X must still be set
        int perms = PERM_R | PERM_A;
        assertFalse(PermissionCheck.check(perms, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 0, 1));
    }

    @Test
    void testMxrDoesNotAffectStore() {
        // MXR should not change store behavior — W must still be set
        int perms = PERM_X | PERM_A | PERM_D;
        assertFalse(PermissionCheck.check(perms, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 1));
    }

    // ========== Combined Scenarios ==========

    @Test
    void testUserPageWithMxr_LoadAllowedInSModeWithSum() {
        // U=1, X=1, R=0, S-mode, SUM=1, MXR=1 → load allowed
        int perms = PERM_X | PERM_U | PERM_A;
        assertTrue(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 1, 1));
    }

    @Test
    void testFullyPermissive_AllAccessTypes() {
        int perms = PERM_R | PERM_W | PERM_X | PERM_A | PERM_D;
        assertTrue(PermissionCheck.check(perms, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
        assertTrue(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
        assertTrue(PermissionCheck.check(perms, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
    }

    @Test
    void testReadOnly_StoreBlocked() {
        int perms = PERM_R | PERM_A | PERM_D;
        assertTrue(PermissionCheck.check(perms, AccessType.LOAD, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
        assertFalse(PermissionCheck.check(perms, AccessType.STORE, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
    }

    @Test
    void testWriteOnly_FetchBlocked() {
        // R=1, W=1, X=0 → fetch denied
        int perms = PERM_R | PERM_W | PERM_A | PERM_D;
        assertFalse(PermissionCheck.check(perms, AccessType.FETCH, PRIVILEGE_MODE.SUPERVISOR, 0, 0));
    }
}
