package com.cburch.logisim.riscv.cpu;

import com.cburch.logisim.riscv.cpu.csrs.PRIVILEGE_MODE;

/**
 * SV32 page permission checks per RISC-V Privileged Spec Section 4.3.2, step 6.
 * Used by both the page table walker and TLB hit path to enforce identical rules.
 */
public class PermissionCheck {

    /**
     * Check if a memory access is permitted given PTE permission bits,
     * access type, privilege mode, and mstatus control bits.
     *
     * @param perms TLB-format permission bits (PERM_R|W|X|U|G|A|D)
     * @param accessType FETCH, LOAD, or STORE
     * @param mode current privilege mode
     * @param sum mstatus.SUM value (0 or 1)
     * @param mxr mstatus.MXR value (0 or 1)
     * @return true if access is permitted, false if page fault should be raised
     */
    public static boolean check(int perms, TranslationLookasideBuffer.AccessType accessType,
                                PRIVILEGE_MODE mode, long sum, long mxr) {
        // A bit must be set for any access
        if ((perms & TranslationLookasideBuffer.PERM_A) == 0) return false;
        // D bit must be set for stores
        if (accessType == TranslationLookasideBuffer.AccessType.STORE
            && (perms & TranslationLookasideBuffer.PERM_D) == 0) return false;

        // U-bit privilege checks
        boolean isUserPage = (perms & TranslationLookasideBuffer.PERM_U) != 0;
        if (mode == PRIVILEGE_MODE.USER && !isUserPage) return false;
        if (mode == PRIVILEGE_MODE.SUPERVISOR && isUserPage) {
            // SUM doesn't help for instruction fetch
            if (accessType == TranslationLookasideBuffer.AccessType.FETCH) return false;
            if (sum == 0) return false;
        }

        // R/W/X permission check (with MXR for loads)
        return switch (accessType) {
            case FETCH -> (perms & TranslationLookasideBuffer.PERM_X) != 0;
            case LOAD -> (perms & TranslationLookasideBuffer.PERM_R) != 0
                || (mxr == 1 && (perms & TranslationLookasideBuffer.PERM_X) != 0);
            case STORE -> (perms & TranslationLookasideBuffer.PERM_W) != 0;
        };
    }
}
