package com.cburch.logisim.riscv;

import org.junit.jupiter.api.Test;
import com.cburch.logisim.Main;

import java.io.*;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertTrue;


public class IntegrationTest {

    @Test
    public void towerOfHanoiTest_WhenGivenArgumentAsConsoleInput_ThenSuccessfullyCalculate() throws IOException,NullPointerException {
        String[] arguments = new String[] { "-b","circuits/towers_of_hanoi/towers_test.circ","-tty", "tty" };

        InputStream fips = new FileInputStream("circuits/towers_of_hanoi/towers_input.txt");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(byteArrayOutputStream);

        System.setIn(fips);
        System.setOut(out);

        Main.main(arguments);

        String consoleOutput = byteArrayOutputStream.toString(Charset.defaultCharset());
        assertTrue(consoleOutput.contains("Result = 00000000"));

        fips.close();
        out.close();
    }
}

