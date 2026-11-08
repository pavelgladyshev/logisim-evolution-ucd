/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.riscv.blockstorage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Where the Block Storage looks for its disk image file (BlockStorage.resolveFile). */
class BlockStorageFileTest {
    @TempDir
    File folder;

    private File circuit() {
        return new File(folder, "computer.circ");
    }

    @Test
    void relativeNameIsInTheCircuitsFolder() {
        assertEquals(new File(folder, "block_storage.bin"),
                BlockStorage.resolveFile("block_storage.bin", circuit()));
    }

    @Test
    void relativePathIsUnderTheCircuitsFolder() {
        assertEquals(new File(new File(folder, "disks"), "a.bin"),
                BlockStorage.resolveFile("disks" + File.separator + "a.bin", circuit()));
    }

    @Test
    void absoluteNameStaysAsItIs() {
        String elsewhere = new File(new File(folder, "elsewhere"), "disk.bin").getPath();
        assertEquals(new File(elsewhere), BlockStorage.resolveFile(elsewhere, circuit()));
    }

    @Test
    void withoutACircuitFileTheNameStaysAsItIs() {
        // a circuit not saved yet: Java takes the name in the folder Logisim was started in
        assertEquals(new File("block_storage.bin"),
                BlockStorage.resolveFile("block_storage.bin", null));
    }

    @Test
    void emptyNameStaysEmpty() {
        assertEquals(new File(""), BlockStorage.resolveFile("", circuit()));
    }
}
