/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.download;

import static com.cburch.logisim.fpga.Strings.S;

import com.cburch.logisim.fpga.data.BoardInformation;
import com.cburch.logisim.fpga.data.DriveStrength;
import com.cburch.logisim.fpga.data.IoStandards;
import com.cburch.logisim.fpga.data.MappableResourcesContainer;
import com.cburch.logisim.fpga.data.PullBehaviors;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.file.FileWriter;
import com.cburch.logisim.fpga.gui.Reporter;
import com.cburch.logisim.fpga.hdlgenerator.TickComponentHdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.ToplevelHdlGeneratorFactory;
import com.cburch.logisim.fpga.settings.VendorSoftware;
import com.cburch.logisim.util.LineBuffer;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Open-source flow for Gowin FPGAs, such as the Sipeed Tang Nano boards: Yosys synth_gowin,
 * nextpnr-himbaechel with its Gowin target, Project Apicula's gowin_pack, and openFPGALoader to load the
 * bitstream. The tool path is a folder with these four programs (normally ~/gowin/bin). The flow reads
 * Verilog.
 *
 * <p>A board file names its FPGA in the vendor's own terms: Part, Package and Speedgrade together are the
 * device nextpnr is given (GW2AR-LV18 + QN88 + C8/I7 is GW2AR-LV18QN88C8/I7), and Family is the chip
 * family that nextpnr and gowin_pack both need beside it (GW2A-18C), since one family covers several
 * packages and some devices are named only through theirs.
 */
public class GowinDownload implements VendorDownload {

  private static final String YOSYS_SCRIPT_FILE = "yosys.ys";
  private static final String CST_FILE = "toplevel.cst";
  private static final String JSON_FILE = "toplevel.json";
  private static final String ROUTED_FILE = "toplevel_pnr.json";
  private static final String BIT_FILE = "toplevel.fs";
  private static final String DEFAULT_IO_STANDARD = "LVCMOS33";

  private final VendorSoftware vendor = VendorSoftware.getSoftware(VendorSoftware.VENDOR_GOWIN);
  private final String scriptPath;
  private final String sandboxPath;
  private final Netlist rootNetList;
  private final BoardInformation boardInfo;
  private final List<String> verilogFiles;
  private final boolean writeToFlash;
  private MappableResourcesContainer mapInfo;

  public GowinDownload(
      String projectPath,
      Netlist rootNetList,
      BoardInformation boardInfo,
      List<String> entities,
      boolean writeToFlash) {
    // absolute, as the tools run in the sandbox directory (the workspace preference may be relative)
    final var projectDir = new File(projectPath).getAbsolutePath() + File.separator;
    this.sandboxPath = DownloadBase.getDirectoryLocation(projectDir, DownloadBase.SANDBOX_PATH);
    this.scriptPath = DownloadBase.getDirectoryLocation(projectDir, DownloadBase.SCRIPT_PATH);
    this.rootNetList = rootNetList;
    this.boardInfo = boardInfo;
    this.verilogFiles = entities;
    this.writeToFlash = writeToFlash;
  }

  /** The device as nextpnr-himbaechel names it, e.g. GW2AR-LV18QN88C8/I7. */
  private String device() {
    final var fpga = boardInfo.fpga;
    return (fpga.getPart() + fpga.getPackage() + fpga.getSpeedGrade()).trim().toUpperCase(Locale.ROOT);
  }

  /** The chip family nextpnr-himbaechel and gowin_pack take with the device, e.g. GW2A-18C. */
  private String family() {
    return boardInfo.fpga.getTechnology().trim().toUpperCase(Locale.ROOT);
  }

  /**
   * Where nextpnr-himbaechel keeps its Gowin chip databases: share/nextpnr/himbaechel/gowin next to the
   * folder the real program is in, which is not always the tool folder - it may hold only a link to a
   * nextpnr installed elsewhere.
   */
  private File chipDbFolder() {
    var program = new File(vendor.getBinaryPath(1));
    try {
      program = program.getCanonicalFile();
    } catch (IOException e) {
      program = program.getAbsoluteFile();
    }
    final var bin = program.getParentFile();
    final var root = bin == null ? null : bin.getParentFile();
    return new File(root, "share" + File.separator + "nextpnr" + File.separator + "himbaechel"
        + File.separator + "gowin");
  }

  @Override
  public int getNumberOfStages() {
    return 3;
  }

  @Override
  public String getStageMessage(int stage) {
    return switch (stage) {
      case 0 -> S.get("GowinYosys");
      case 1 -> S.get("GowinNextpnr");
      case 2 -> S.get("GowinPack");
      default -> "unknown";
    };
  }

  @Override
  public ProcessBuilder performStep(int stage) {
    final var command = LineBuffer.getBuffer();
    switch (stage) {
      case 0 -> command
          .add(vendor.getBinaryPath(0))
          .add("-q")
          .add("-l").add("yosys.log")
          .add("-s").add(scriptPath + YOSYS_SCRIPT_FILE);
      case 1 -> command
          .add(vendor.getBinaryPath(1))
          .add("--json").add(JSON_FILE)
          .add("--write").add(ROUTED_FILE)
          .add("--device").add(device())
          .add("--vopt").add("family=" + family())
          .add("--vopt").add("cst=" + scriptPath + CST_FILE)
          .add("--freq").add(String.format(Locale.US, "%.3f", boardInfo.fpga.getClockFrequency() / 1.0e6))
          // latches built from gates, as circuits for teaching have them, are combinational loops
          .add("--ignore-loops")
          // nextpnr times every path in one cycle of the board clock, but Logisim's registers run on that clock
          // and change only on their Clock component's tick, at most once every four cycles, so the logic
          // between them has that long to settle: the A4 BEAG reports 26 to 46 MHz against 27 depending on
          // placement, and works at 26. A shortfall is a warning in the log rather than the end of the build.
          .add("--timing-allow-fail")
          .add("--log").add("nextpnr.log");
      case 2 -> command
          .add(vendor.getBinaryPath(2))
          .add("-d").add(family())
          // The pins of the slave SPI configuration port are the circuit's, like every pin on the board (on a
          // Tang Nano 20K they are 52-56, the audio and HDMI DDC lines). Left a configuration port, it takes
          // what a circuit drives there for commands, after which the next load stops at "Erase SRAM" until
          // the FPGA is reset.
          .add("--sspi_as_gpio")
          .add("-o").add(BIT_FILE)
          .add(ROUTED_FILE);
      default -> {
        return null;
      }
    }
    return toolProcess(command.get());
  }

  /**
   * A tool run in the sandbox. On Windows the toolchain is OSS CAD Suite, whose programs find their DLLs in the
   * lib folder beside bin rather than next to themselves, because its environment.bat puts both on the PATH;
   * Logisim starts them directly, so it does the same.
   */
  private ProcessBuilder toolProcess(List<String> command) {
    final var process = new ProcessBuilder(command);
    process.directory(new File(sandboxPath));
    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
      final var bin = new File(vendor.getToolPath()).getAbsoluteFile();
      final var lib = new File(bin.getParentFile(), "lib");
      if (lib.isDirectory()) {
        final var path = process.environment().getOrDefault("PATH", "");
        process.environment().put("PATH", bin + File.pathSeparator + lib + File.pathSeparator + path);
      }
    }
    return process;
  }

  @Override
  public boolean readyForDownload() {
    return new File(sandboxPath + BIT_FILE).exists();
  }

  /** openFPGALoader options that select the board for the boards it knows by name. */
  private List<String> boardOptions() {
    final var board = boardInfo.getBoardName().toUpperCase(Locale.ROOT).replace("_", "");
    if (board.contains("TANGNANO20K")) return List.of("-b", "tangnano20k");
    if (board.contains("TANGNANO9K")) return List.of("-b", "tangnano9k");
    if (board.contains("TANGNANO4K")) return List.of("-b", "tangnano4k");
    if (board.contains("TANGNANO1K")) return List.of("-b", "tangnano1k");
    // other boards: the FTDI-style cable the Tang debuggers present, and the FPGA found on the chain
    return List.of("-c", "ft2232");
  }

  @Override
  public ProcessBuilder downloadToBoard() {
    final var command = LineBuffer.getBuffer();
    command.add(vendor.getBinaryPath(3));
    for (final var option : boardOptions()) command.add(option);
    if (writeToFlash) command.add("-f");
    command.add(BIT_FILE);
    return toolProcess(command.get());
  }

  @Override
  public boolean createDownloadScripts() {
    final var chipDbs = chipDbFolder();
    final var chipDb = new File(chipDbs, "chipdb-" + family() + ".bin");
    // only when the folder is where it should be: a nextpnr laid out differently reports a missing
    // database itself
    if (chipDbs.isDirectory() && !chipDb.exists()) {
      Reporter.report.addFatalError(S.get("GowinNoChipDb", family(), chipDbs.getPath()));
      return false;
    }
    var yosysScript = FileWriter.getFilePointer(scriptPath, YOSYS_SCRIPT_FILE);
    var constraintFile = FileWriter.getFilePointer(scriptPath, CST_FILE);
    if (yosysScript == null || constraintFile == null) {
      yosysScript = new File(scriptPath + YOSYS_SCRIPT_FILE);
      constraintFile = new File(scriptPath + CST_FILE);
      return yosysScript.exists() && constraintFile.exists();
    }
    final var script = LineBuffer.getBuffer();
    for (final var file : verilogFiles) script.add("read_verilog -sv \"{{1}}\"", new File(file).getAbsolutePath());
    final var top = ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME;
    // A Gowin flip-flop starts at the value of its asynchronous set or reset, so one with an asynchronous set
    // cannot start at 0, and synth_gowin refuses it. Such flip-flops (Logisim's with Preset wired) lose their
    // start value, which Logisim gives for simulation only: with Reset wired too, synth_gowin builds them from
    // two flip-flops and a latch, which start at 0; with Preset alone they start at 1. The design is flattened
    // first, so that only the flip-flops whose Preset is wired in the circuit are touched.
    script
        .add("hierarchy -top {{1}}", top)
        .add("proc")
        .add("flatten")
        .add("opt -nodffe -nosdff")
        .add("setattr -unset init t:$dffsr* t:$adff* r:ARST_VALUE=1'1 %i %u %co:+[Q] w:* %i")
        .add("synth_gowin -top {{1}} -json {{2}}", top, JSON_FILE)
        .add("stat");
    if (!FileWriter.writeContents(yosysScript, script.get())) return false;

    final var constraints = LineBuffer.getBuffer();
    if (rootNetList.numberOfClockTrees() > 0 || rootNetList.requiresGlobalClockConnection()) {
      final var clock = TickComponentHdlGeneratorFactory.FPGA_CLOCK;
      constraints.add("IO_LOC \"{{1}}\" {{2}};", clock, boardInfo.fpga.getClockPinLocation());
      addIoProperties(constraints, boardInfo.fpga.getClockStandard(), PullBehaviors.UNKNOWN,
          DriveStrength.UNKNOWN, clock);
    }
    for (final var key : mapInfo.getMappableResources().keySet()) {
      final var map = mapInfo.getMappableResources().get(key);
      for (var i = 0; i < map.getNrOfPins(); i++) {
        if (map.isMapped(i) && !map.isOpenMapped(i) && !map.isConstantMapped(i) && !map.isInternalMapped(i)) {
          final var netName = (map.isExternalInverted(i) ? "n_" : "") + map.getHdlString(i);
          constraints.add("IO_LOC \"{{1}}\" {{2}};", netName, map.getPinLocation(i));
          final var info = map.getFpgaInfo(i);
          if (info != null) {
            addIoProperties(constraints, info.getIoStandard(), info.getPullBehavior(), info.getDrive(), netName);
          } else {
            addIoProperties(constraints, IoStandards.UNKNOWN, PullBehaviors.UNKNOWN, DriveStrength.UNKNOWN, netName);
          }
        }
      }
    }
    for (final var pin : DownloadBase.getScanningMaps(mapInfo, rootNetList, boardInfo)) {
      constraints.add("IO_LOC \"{{1}}\" {{2}};", pin.hdlSignal(), pin.pinLocation());
      addIoProperties(constraints, pin.ioStandard(), pin.pullBehavior(), pin.driveStrength(), pin.hdlSignal());
    }
    return FileWriter.writeContents(constraintFile, constraints.get());
  }

  /**
   * One IO_PORT line in Gowin's terms: IO_TYPE for the standard (LVTTL is LVTTL33 there), PULL_MODE UP,
   * DOWN or NONE for float, and DRIVE in milliamperes, of which 3.3 V CMOS offers 4 upwards.
   */
  private static void addIoProperties(
      LineBuffer contents, char ioStandard, char pullBehavior, char driveStrength, String netName) {
    final var known = ioStandard != IoStandards.UNKNOWN && ioStandard != IoStandards.DEFAULT_STANDARD;
    var standard = known ? IoStandards.getConstraintedIoStandard(ioStandard).trim() : "";
    if (standard.isEmpty()) standard = DEFAULT_IO_STANDARD;
    if (standard.equalsIgnoreCase("LVTTL")) standard = "LVTTL33";
    final var line = new StringBuilder("IO_PORT \"" + netName + "\" IO_TYPE=" + standard);
    if (pullBehavior == PullBehaviors.PULL_UP) line.append(" PULL_MODE=UP");
    else if (pullBehavior == PullBehaviors.PULL_DOWN) line.append(" PULL_MODE=DOWN");
    else if (pullBehavior == PullBehaviors.FLOAT) line.append(" PULL_MODE=NONE");
    if (driveStrength != DriveStrength.UNKNOWN && driveStrength != DriveStrength.DEFAULT_STENGTH) {
      final var milliamperes = Integer.parseInt(DriveStrength.getConstrainedDriveStrength(driveStrength).trim());
      line.append(" DRIVE=").append(Math.max(4, milliamperes));
    }
    contents.add(line.append(';').toString());
  }

  @Override
  public void setMappableResources(MappableResourcesContainer resources) {
    mapInfo = resources;
  }

  @Override
  public boolean isBoardConnected() {
    // openFPGALoader reports a missing board itself
    return true;
  }
}
