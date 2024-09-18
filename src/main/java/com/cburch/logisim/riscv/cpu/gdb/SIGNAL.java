package com.cburch.logisim.riscv.cpu.gdb;

public enum SIGNAL {

    GDB_SIGNAL_0(0, "0", "Signal 0"),
    GDB_SIGNAL_HUP(1, "SIGHUP", "Hangup"),
    GDB_SIGNAL_INT(2, "SIGINT", "Interrupt"),
    GDB_SIGNAL_QUIT(3, "SIGQUIT", "Quit"),
    GDB_SIGNAL_ILL(4, "SIGILL", "Illegal instruction"),
    GDB_SIGNAL_TRAP(5, "SIGTRAP", "Trace/breakpoint trap"),
    GDB_SIGNAL_ABRT(6, "SIGABRT", "Aborted"),
    GDB_SIGNAL_EMT(7, "SIGEMT", "Emulation trap"),
    GDB_SIGNAL_FPE(8, "SIGFPE", "Arithmetic exception"),
    GDB_SIGNAL_KILL(9, "SIGKILL", "Killed"),
    GDB_SIGNAL_BUS(10, "SIGBUS", "Bus error"),
    GDB_SIGNAL_SEGV(11, "SIGSEGV", "Segmentation fault"),
    GDB_SIGNAL_SYS(12, "SIGSYS", "Bad system call"),
    GDB_SIGNAL_PIPE(13, "SIGPIPE", "Broken pipe"),
    GDB_SIGNAL_ALRM(14, "SIGALRM", "Alarm clock"),
    GDB_SIGNAL_TERM(15, "SIGTERM", "Terminated"),
    GDB_SIGNAL_URG(16, "SIGURG", "Urgent I/O condition"),
    GDB_SIGNAL_STOP(17, "SIGSTOP", "Stopped (signal)"),
    GDB_SIGNAL_TSTP(18, "SIGTSTP", "Stopped (user)"),
    GDB_SIGNAL_CONT(19, "SIGCONT", "Continued"),
    GDB_SIGNAL_CHLD(20, "SIGCHLD", "Child status changed"),
    GDB_SIGNAL_TTIN(21, "SIGTTIN", "Stopped (tty input)"),
    GDB_SIGNAL_TTOU(22, "SIGTTOU", "Stopped (tty output)"),
    GDB_SIGNAL_IO(23, "SIGIO", "I/O possible"),
    GDB_SIGNAL_XCPU(24, "SIGXCPU", "CPU time limit exceeded"),
    GDB_SIGNAL_XFSZ(25, "SIGXFSZ", "File size limit exceeded"),
    GDB_SIGNAL_VTALRM(26, "SIGVTALRM", "Virtual timer expired"),
    GDB_SIGNAL_PROF(27, "SIGPROF", "Profiling timer expired"),
    GDB_SIGNAL_WINCH(28, "SIGWINCH", "Window size changed"),
    GDB_SIGNAL_LOST(29, "SIGLOST", "Resource lost"),
    GDB_SIGNAL_USR1(30, "SIGUSR1", "User defined signal 1"),
    GDB_SIGNAL_USR2(31, "SIGUSR2", "User defined signal 2"),
    GDB_SIGNAL_PWR(32, "SIGPWR", "Power fail/restart"),
    /* Similar to SIGIO.  Perhaps they should have the same number.  */
    GDB_SIGNAL_POLL(33, "SIGPOLL", "Pollable event occurred"),
    GDB_SIGNAL_WIND(34, "SIGWIND", "SIGWIND"),
    GDB_SIGNAL_PHONE(35, "SIGPHONE", "SIGPHONE"),
    GDB_SIGNAL_WAITING(36, "SIGWAITING", "Process's LWPs are blocked"),
    GDB_SIGNAL_LWP(37, "SIGLWP", "Signal LWP"),
    GDB_SIGNAL_DANGER(38, "SIGDANGER", "Swap space dangerously low"),
    GDB_SIGNAL_GRANT(39, "SIGGRANT", "Monitor mode granted"),
    GDB_SIGNAL_RETRACT(40, "SIGRETRACT",
            "Need to relinquish monitor mode"),
    GDB_SIGNAL_MSG(41, "SIGMSG", "Monitor mode data available"),
    GDB_SIGNAL_SOUND(42, "SIGSOUND", "Sound completed"),
    GDB_SIGNAL_SAK(43, "SIGSAK", "Secure attention"),
    GDB_SIGNAL_PRIO(44, "SIGPRIO", "SIGPRIO"),
    GDB_SIGNAL_REALTIME_33(45, "SIG33", "Real-time event 33"),
    GDB_SIGNAL_REALTIME_34(46, "SIG34", "Real-time event 34"),
    GDB_SIGNAL_REALTIME_35(47, "SIG35", "Real-time event 35"),
    GDB_SIGNAL_REALTIME_36(48, "SIG36", "Real-time event 36"),
    GDB_SIGNAL_REALTIME_37(49, "SIG37", "Real-time event 37"),
    GDB_SIGNAL_REALTIME_38(50, "SIG38", "Real-time event 38"),
    GDB_SIGNAL_REALTIME_39(51, "SIG39", "Real-time event 39"),
    GDB_SIGNAL_REALTIME_40(52, "SIG40", "Real-time event 40"),
    GDB_SIGNAL_REALTIME_41(53, "SIG41", "Real-time event 41"),
    GDB_SIGNAL_REALTIME_42(54, "SIG42", "Real-time event 42"),
    GDB_SIGNAL_REALTIME_43(55, "SIG43", "Real-time event 43"),
    GDB_SIGNAL_REALTIME_44(56, "SIG44", "Real-time event 44"),
    GDB_SIGNAL_REALTIME_45(57, "SIG45", "Real-time event 45"),
    GDB_SIGNAL_REALTIME_46(58, "SIG46", "Real-time event 46"),
    GDB_SIGNAL_REALTIME_47(59, "SIG47", "Real-time event 47"),
    GDB_SIGNAL_REALTIME_48(60, "SIG48", "Real-time event 48"),
    GDB_SIGNAL_REALTIME_49(61, "SIG49", "Real-time event 49"),
    GDB_SIGNAL_REALTIME_50(62, "SIG50", "Real-time event 50"),
    GDB_SIGNAL_REALTIME_51(63, "SIG51", "Real-time event 51"),
    GDB_SIGNAL_REALTIME_52(64, "SIG52", "Real-time event 52"),
    GDB_SIGNAL_REALTIME_53(65, "SIG53", "Real-time event 53"),
    GDB_SIGNAL_REALTIME_54(66, "SIG54", "Real-time event 54"),
    GDB_SIGNAL_REALTIME_55(67, "SIG55", "Real-time event 55"),
    GDB_SIGNAL_REALTIME_56(68, "SIG56", "Real-time event 56"),
    GDB_SIGNAL_REALTIME_57(69, "SIG57", "Real-time event 57"),
    GDB_SIGNAL_REALTIME_58(70, "SIG58", "Real-time event 58"),
    GDB_SIGNAL_REALTIME_59(71, "SIG59", "Real-time event 59"),
    GDB_SIGNAL_REALTIME_60(72, "SIG60", "Real-time event 60"),
    GDB_SIGNAL_REALTIME_61(73, "SIG61", "Real-time event 61"),
    GDB_SIGNAL_REALTIME_62(74, "SIG62", "Real-time event 62"),
    GDB_SIGNAL_REALTIME_63(75, "SIG63", "Real-time event 63"),
    /* Used internally by Solaris threads.  See signal(5) on Solaris.  */
    GDB_SIGNAL_CANCEL(76, "SIGCANCEL", "LWP internal signal"),
    /* Yes, this pains me, too.  But LynxOS didn't have SIG32, and now
       GNU/Linux does, and we can't disturb the numbering, since it's
       part of the remote protocol.  Note that in some GDB's
       GDB_SIGNAL_REALTIME_32 is number 76.  */
    GDB_SIGNAL_REALTIME_32(77, "SIG32", "Real-time event 32"),
    /* Yet another pain, IRIX 6 has SIG64. */
    GDB_SIGNAL_REALTIME_64(78, "SIG64", "Real-time event 64"),
    /* Yet another pain, GNU/Linux MIPS might go up to 128. */
    GDB_SIGNAL_REALTIME_65(79, "SIG65", "Real-time event 65"),
    GDB_SIGNAL_REALTIME_66(80, "SIG66", "Real-time event 66"),
    GDB_SIGNAL_REALTIME_67(81, "SIG67", "Real-time event 67"),
    GDB_SIGNAL_REALTIME_68(82, "SIG68", "Real-time event 68"),
    GDB_SIGNAL_REALTIME_69(83, "SIG69", "Real-time event 69"),
    GDB_SIGNAL_REALTIME_70(84, "SIG70", "Real-time event 70"),
    GDB_SIGNAL_REALTIME_71(85, "SIG71", "Real-time event 71"),
    GDB_SIGNAL_REALTIME_72(86, "SIG72", "Real-time event 72"),
    GDB_SIGNAL_REALTIME_73(87, "SIG73", "Real-time event 73"),
    GDB_SIGNAL_REALTIME_74(88, "SIG74", "Real-time event 74"),
    GDB_SIGNAL_REALTIME_75(89, "SIG75", "Real-time event 75"),
    GDB_SIGNAL_REALTIME_76(90, "SIG76", "Real-time event 76"),
    GDB_SIGNAL_REALTIME_77(91, "SIG77", "Real-time event 77"),
    GDB_SIGNAL_REALTIME_78(92, "SIG78", "Real-time event 78"),
    GDB_SIGNAL_REALTIME_79(93, "SIG79", "Real-time event 79"),
    GDB_SIGNAL_REALTIME_80(94, "SIG80", "Real-time event 80"),
    GDB_SIGNAL_REALTIME_81(95, "SIG81", "Real-time event 81"),
    GDB_SIGNAL_REALTIME_82(96, "SIG82", "Real-time event 82"),
    GDB_SIGNAL_REALTIME_83(97, "SIG83", "Real-time event 83"),
    GDB_SIGNAL_REALTIME_84(98, "SIG84", "Real-time event 84"),
    GDB_SIGNAL_REALTIME_85(99, "SIG85", "Real-time event 85"),
    GDB_SIGNAL_REALTIME_86(100, "SIG86", "Real-time event 86"),
    GDB_SIGNAL_REALTIME_87(101, "SIG87", "Real-time event 87"),
    GDB_SIGNAL_REALTIME_88(102, "SIG88", "Real-time event 88"),
    GDB_SIGNAL_REALTIME_89(103, "SIG89", "Real-time event 89"),
    GDB_SIGNAL_REALTIME_90(104, "SIG90", "Real-time event 90"),
    GDB_SIGNAL_REALTIME_91(105, "SIG91", "Real-time event 91"),
    GDB_SIGNAL_REALTIME_92(106, "SIG92", "Real-time event 92"),
    GDB_SIGNAL_REALTIME_93(107, "SIG93", "Real-time event 93"),
    GDB_SIGNAL_REALTIME_94(108, "SIG94", "Real-time event 94"),
    GDB_SIGNAL_REALTIME_95(109, "SIG95", "Real-time event 95"),
    GDB_SIGNAL_REALTIME_96(110, "SIG96", "Real-time event 96"),
    GDB_SIGNAL_REALTIME_97(111, "SIG97", "Real-time event 97"),
    GDB_SIGNAL_REALTIME_98(112, "SIG98", "Real-time event 98"),
    GDB_SIGNAL_REALTIME_99(113, "SIG99", "Real-time event 99"),
    GDB_SIGNAL_REALTIME_100(114, "SIG100", "Real-time event 100"),
    GDB_SIGNAL_REALTIME_101(115, "SIG101", "Real-time event 101"),
    GDB_SIGNAL_REALTIME_102(116, "SIG102", "Real-time event 102"),
    GDB_SIGNAL_REALTIME_103(117, "SIG103", "Real-time event 103"),
    GDB_SIGNAL_REALTIME_104(118, "SIG104", "Real-time event 104"),
    GDB_SIGNAL_REALTIME_105(119, "SIG105", "Real-time event 105"),
    GDB_SIGNAL_REALTIME_106(120, "SIG106", "Real-time event 106"),
    GDB_SIGNAL_REALTIME_107(121, "SIG107", "Real-time event 107"),
    GDB_SIGNAL_REALTIME_108(122, "SIG108", "Real-time event 108"),
    GDB_SIGNAL_REALTIME_109(123, "SIG109", "Real-time event 109"),
    GDB_SIGNAL_REALTIME_110(124, "SIG110", "Real-time event 110"),
    GDB_SIGNAL_REALTIME_111(125, "SIG111", "Real-time event 111"),
    GDB_SIGNAL_REALTIME_112(126, "SIG112", "Real-time event 112"),
    GDB_SIGNAL_REALTIME_113(127, "SIG113", "Real-time event 113"),
    GDB_SIGNAL_REALTIME_114(128, "SIG114", "Real-time event 114"),
    GDB_SIGNAL_REALTIME_115(129, "SIG115", "Real-time event 115"),
    GDB_SIGNAL_REALTIME_116(130, "SIG116", "Real-time event 116"),
    GDB_SIGNAL_REALTIME_117(131, "SIG117", "Real-time event 117"),
    GDB_SIGNAL_REALTIME_118(132, "SIG118", "Real-time event 118"),
    GDB_SIGNAL_REALTIME_119(133, "SIG119", "Real-time event 119"),
    GDB_SIGNAL_REALTIME_120(134, "SIG120", "Real-time event 120"),
    GDB_SIGNAL_REALTIME_121(135, "SIG121", "Real-time event 121"),
    GDB_SIGNAL_REALTIME_122(136, "SIG122", "Real-time event 122"),
    GDB_SIGNAL_REALTIME_123(137, "SIG123", "Real-time event 123"),
    GDB_SIGNAL_REALTIME_124(138, "SIG124", "Real-time event 124"),
    GDB_SIGNAL_REALTIME_125(139, "SIG125", "Real-time event 125"),
    GDB_SIGNAL_REALTIME_126(140, "SIG126", "Real-time event 126"),
    GDB_SIGNAL_REALTIME_127(141, "SIG127", "Real-time event 127"),
    GDB_SIGNAL_INFO(142, "SIGINFO", "Information request"),
    /* Some signal we don't know about.  */
    GDB_SIGNAL_UNKNOWN(143, "NULL", "Unknown signal"),
    /* Use whatever signal we use when one is not specifically specified
       (for passing to proceed and so on),.  */
    GDB_SIGNAL_DEFAULT(144, "NULL", "Internal error: printing GDB_SIGNAL_DEFAULT"),
    /* Mach exceptions.  In versions of GDB before 5.2, these were just before
       GDB_SIGNAL_INFO if you were compiling on a Mach host (and missing
       otherwise),.  */
    GDB_EXC_BAD_ACCESS(145, "EXC_BAD_ACCESS", "Could not access memory"),
    GDB_EXC_BAD_INSTRUCTION(146, "EXC_BAD_INSTRUCTION", "Illegal instruction/operand"),
    GDB_EXC_ARITHMETIC(147, "EXC_ARITHMETIC", "Arithmetic exception"),
    GDB_EXC_EMULATION(148, "EXC_EMULATION", "Emulation instruction"),
    GDB_EXC_SOFTWARE(149, "EXC_SOFTWARE", "Software generated exception"),
    GDB_EXC_BREAKPOINT(150, "EXC_BREAKPOINT", "Breakpoint"),
    /* If you are adding a new signal, add it just above this comment.  */
    /* Last and unused enum value, for sizing arrays, etc.  */
    GDB_SIGNAL_LAST(151, "NULL", "GDB_SIGNAL_LAST");

    final int code;
    final String name;
    final String message;

    SIGNAL(int code, String name, String message) {
        this.code = code;
        this.name = name;
        this.message = message;
    }

    public String getCode() {
        return String.format("S%02X", code);
    }

    public String getName() {
        return name;
    }

    public String getMessage() {
        return String.format("E.%s",message);
    }
}
