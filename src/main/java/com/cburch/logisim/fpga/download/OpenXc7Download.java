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
import java.util.List;
import java.util.Locale;

/**
 * Open-source flow for Xilinx 7-series FPGAs (openXC7): Yosys synth_xilinx, nextpnr-xilinx, Project X-Ray's
 * fasm2frames and xc7frames2bit, and openFPGALoader to load the bitstream. The tool path is a folder with
 * these five programs (normally ~/openxc7/bin, made by setup_toolchain.sh); the nextpnr-xilinx chip database
 * of the board's part is ../chipdb/&lt;part&gt;.bin and the Project X-Ray database ../prjxray-db/&lt;family&gt;.
 * The flow reads Verilog.
 */
public class OpenXc7Download implements VendorDownload {

  private static final String YOSYS_SCRIPT_FILE = "yosys.ys";
  private static final String XDC_FILE = "toplevel.xdc";
  private static final String JSON_FILE = "toplevel.json";
  private static final String FASM_FILE = "toplevel.fasm";
  private static final String FRAMES_FILE = "toplevel.frames";
  private static final String BIT_FILE = "toplevel.bit";
  private static final String DEFAULT_IO_STANDARD = "LVCMOS33";

  private final VendorSoftware vendor = VendorSoftware.getSoftware(VendorSoftware.VENDOR_OPENXC7);
  private final String scriptPath;
  private final String sandboxPath;
  private final Netlist rootNetList;
  private final BoardInformation boardInfo;
  private final List<String> verilogFiles;
  private final boolean writeToFlash;
  private MappableResourcesContainer mapInfo;

  public OpenXc7Download(
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

  /** Full part name as used by nextpnr-xilinx and Project X-Ray, e.g. xc7a35tcpg236-1. */
  private String partName() {
    final var fpga = boardInfo.fpga;
    var speed = fpga.getSpeedGrade().trim();
    if (!speed.isEmpty() && !speed.startsWith("-")) speed = "-" + speed;
    return (fpga.getPart() + fpga.getPackage() + speed).toLowerCase(Locale.ROOT);
  }

  private String family() {
    final var part = partName();
    if (part.startsWith("xc7a")) return "artix7";
    if (part.startsWith("xc7k")) return "kintex7";
    if (part.startsWith("xc7s")) return "spartan7";
    if (part.startsWith("xc7z")) return "zynq7";
    if (part.startsWith("xc7v")) return "virtex7";
    return "artix7";
  }

  private File toolRoot() {
    final var bin = new File(vendor.getToolPath());
    final var parent = bin.getAbsoluteFile().getParentFile();
    return parent == null ? bin : parent;
  }

  private File chipDb() {
    return new File(new File(toolRoot(), "chipdb"), partName() + ".bin");
  }

  private File databaseRoot() {
    return new File(new File(toolRoot(), "prjxray-db"), family());
  }

  @Override
  public int getNumberOfStages() {
    return 4;
  }

  @Override
  public String getStageMessage(int stage) {
    return switch (stage) {
      case 0 -> S.get("OpenXc7Yosys");
      case 1 -> S.get("OpenXc7Nextpnr");
      case 2 -> S.get("OpenXc7Fasm2Frames");
      case 3 -> S.get("OpenXc7Frames2Bit");
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
          .add("--chipdb").add(chipDb().getPath())
          .add("--xdc").add(scriptPath + XDC_FILE)
          .add("--json").add(JSON_FILE)
          .add("--fasm").add(FASM_FILE)
          .add("--log").add("nextpnr.log");
      case 2 -> command
          .add(vendor.getBinaryPath(2))
          .add("--part").add(partName())
          .add("--db-root").add(databaseRoot().getPath())
          .add(FASM_FILE)
          .add(FRAMES_FILE);
      case 3 -> command
          .add(vendor.getBinaryPath(3))
          .add("--part_file").add(new File(new File(databaseRoot(), partName()), "part.yaml").getPath())
          .add("--part_name").add(partName())
          .add("--frm_file").add(FRAMES_FILE)
          .add("--output_file").add(BIT_FILE);
      default -> {
        return null;
      }
    }
    final var process = new ProcessBuilder(command.get());
    process.directory(new File(sandboxPath));
    return process;
  }

  @Override
  public boolean readyForDownload() {
    return new File(sandboxPath + BIT_FILE).exists();
  }

  /** openFPGALoader options that select the board (cable and flash) for boards it knows by name. */
  private List<String> boardOptions() {
    final var part = partName();
    final var board = boardInfo.getBoardName().toUpperCase(Locale.ROOT);
    if (board.contains("CMOD") && part.startsWith("xc7a35t")) return List.of("-b", "cmoda7_35t");
    if (board.contains("CMOD") && part.startsWith("xc7a15t")) return List.of("-b", "cmoda7_15t");
    if (board.contains("BASYS3")) return List.of("-b", "basys3");
    if (board.contains("ARTY") && part.startsWith("xc7a35t")) return List.of("-b", "arty_a7_35t");
    if (board.contains("ARTY") && part.startsWith("xc7a100t")) return List.of("-b", "arty_a7_100t");
    // other boards: Digilent-style FTDI cable, part given for flash programming
    return List.of("-c", "digilent", "--fpga-part", part.replaceAll("-[0-9a-z]+$", ""));
  }

  @Override
  public ProcessBuilder downloadToBoard() {
    final var command = LineBuffer.getBuffer();
    command.add(vendor.getBinaryPath(4));
    for (final var option : boardOptions()) command.add(option);
    if (writeToFlash) command.add("-f");
    command.add(BIT_FILE);
    final var process = new ProcessBuilder(command.get());
    process.directory(new File(sandboxPath));
    return process;
  }

  @Override
  public boolean createDownloadScripts() {
    if (!chipDb().exists()) {
      Reporter.report.addFatalError(S.get("OpenXc7NoChipDb", partName(),
          new File(new File(toolRoot(), "bin"), "openxc7-chipdb").getPath() + " " + partName()));
      return false;
    }
    var yosysScript = FileWriter.getFilePointer(scriptPath, YOSYS_SCRIPT_FILE);
    var constraintFile = FileWriter.getFilePointer(scriptPath, XDC_FILE);
    if (yosysScript == null || constraintFile == null) {
      yosysScript = new File(scriptPath + YOSYS_SCRIPT_FILE);
      constraintFile = new File(scriptPath + XDC_FILE);
      return yosysScript.exists() && constraintFile.exists();
    }
    final var script = LineBuffer.getBuffer();
    for (final var file : verilogFiles) script.add("read_verilog -sv \"{{1}}\"", new File(file).getAbsolutePath());
    script
        .add("synth_xilinx -flatten -abc9 -arch xc7 -top {{1}}", ToplevelHdlGeneratorFactory.FPGA_TOP_LEVEL_NAME)
        .add("write_json {{1}}", JSON_FILE)
        .add("stat");
    if (!FileWriter.writeContents(yosysScript, script.get())) return false;

    final var constraints = LineBuffer.getBuffer();
    if (rootNetList.numberOfClockTrees() > 0 || rootNetList.requiresGlobalClockConnection()) {
      final var clock = TickComponentHdlGeneratorFactory.FPGA_CLOCK;
      constraints.add("set_property PACKAGE_PIN {{1}} [get_ports {{{2}}}]", boardInfo.fpga.getClockPinLocation(), clock);
      final var clockStandard = boardInfo.fpga.getClockStandard();
      constraints.add("set_property IOSTANDARD {{1}} [get_ports {{{2}}}]",
          (clockStandard != IoStandards.DEFAULT_STANDARD && clockStandard != IoStandards.UNKNOWN)
              ? IoStandards.BEHAVIOR_STRINGS[clockStandard] : DEFAULT_IO_STANDARD, clock);
      final var period = 1.0e9 / boardInfo.fpga.getClockFrequency();
      constraints.add("create_clock -period {{1}} [get_ports {{{2}}}]", String.format(Locale.US, "%.3f", period), clock);
    }
    for (final var key : mapInfo.getMappableResources().keySet()) {
      final var map = mapInfo.getMappableResources().get(key);
      for (var i = 0; i < map.getNrOfPins(); i++) {
        if (map.isMapped(i) && !map.isOpenMapped(i) && !map.isConstantMapped(i) && !map.isInternalMapped(i)) {
          final var netName = (map.isExternalInverted(i) ? "n_" : "") + map.getHdlString(i);
          constraints.add("set_property PACKAGE_PIN {{1}} [get_ports {{{2}}}]", map.getPinLocation(i), netName);
          final var info = map.getFpgaInfo(i);
          if (info != null) {
            addIoProperties(constraints, info.getIoStandard(), info.getPullBehavior(), info.getDrive(), netName);
          } else {
            constraints.add("set_property IOSTANDARD {{1}} [get_ports {{{2}}}]", DEFAULT_IO_STANDARD, netName);
          }
        }
      }
    }
    for (final var pin : DownloadBase.getScanningMaps(mapInfo, rootNetList, boardInfo)) {
      constraints.add("set_property PACKAGE_PIN {{1}} [get_ports {{{2}}}]", pin.pinLocation(), pin.hdlSignal());
      addIoProperties(constraints, pin.ioStandard(), pin.pullBehavior(), pin.driveStrength(), pin.hdlSignal());
    }
    return FileWriter.writeContents(constraintFile, constraints.get());
  }

  /** I/O properties in the form nextpnr-xilinx understands (PULLTYPE instead of Vivado's PULLUP TRUE). */
  private static void addIoProperties(
      LineBuffer contents, char ioStandard, char pullBehavior, char driveStrength, String netName) {
    // nextpnr-xilinx needs an I/O standard on every pin: 3.3 V CMOS when the board file does not give one
    final var standard = (ioStandard != IoStandards.UNKNOWN && ioStandard != IoStandards.DEFAULT_STANDARD)
        ? IoStandards.getConstraintedIoStandard(ioStandard) : "";
    contents.add("set_property IOSTANDARD {{1}} [get_ports {{{2}}}]",
        standard.isEmpty() ? DEFAULT_IO_STANDARD : standard, netName);
    if (pullBehavior == PullBehaviors.PULL_DOWN || pullBehavior == PullBehaviors.PULL_UP) {
      contents.add("set_property PULLTYPE {{1}} [get_ports {{{2}}}]",
          PullBehaviors.getConstrainedPullString(pullBehavior), netName);
    }
    if (driveStrength != DriveStrength.UNKNOWN && driveStrength != DriveStrength.DEFAULT_STENGTH) {
      contents.add("set_property DRIVE {{1}} [get_ports {{{2}}}]",
          DriveStrength.getConstrainedDriveStrength(driveStrength).trim(), netName);
    }
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
