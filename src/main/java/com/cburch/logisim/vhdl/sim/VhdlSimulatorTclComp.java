/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.vhdl.sim;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.std.hdl.VhdlEntityComponent;
import com.cburch.logisim.util.FileUtil;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.vhdl.base.VhdlEntity;
import com.cburch.logisim.vhdl.base.VhdlSimConstants;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The VHDL source file have to be compiled before they can be simulated. This is done by the
 * following generated script. The script is called by the run.tcl script that is in the resource
 * folders.
 *
 * @author christian.mueller@heig-vd.ch
 */
public class VhdlSimulatorTclComp {

  static final Logger logger = LoggerFactory.getLogger(VhdlSimulatorTclComp.class);

  private boolean valid = false;
  private final VhdlSimulatorTop vsim;

  public VhdlSimulatorTclComp(VhdlSimulatorTop vs) {
    vsim = vs;
  }

  public void fireInvalidated() {
    valid = false;
  }

  public void generate(List<Component> comps) {

    /* Do not generate if file is already valid */
    if (valid) return;

    final var compFiles = new StringBuilder();
    compFiles.append("Autogenerated by logisim");
    compFiles.append(System.getProperty("line.separator"));

    /* For each vhdl entity */
    for (final var comp : comps) {
      if (comp.getFactory().getClass().equals(VhdlEntity.class)
          || comp.getFactory().getClass().equals(VhdlEntityComponent.class)) {

        final var state = vsim.getProject().getCircuitState().getInstanceState(comp);
        final var fact = comp.getFactory();
        final var componentName = (fact instanceof VhdlEntity)
            ? ((VhdlEntity) fact).getSimName(state.getInstance().getAttributeSet())
            : ((VhdlEntityComponent) fact).getSimName(state.getInstance().getAttributeSet());

        compFiles.append(VhdlSimConstants.VHDL_COMPILE_COMMAND)
            .append(componentName)
            .append(".vhdl")
            .append(System.getProperty("line.separator"));
      }
    }

    /*
     * Replace template blocks by generated data
     */
    String template;
    try {
      template =
          new String(
              FileUtil.getBytes(
                  this.getClass()
                      .getResourceAsStream((VhdlSimConstants.SIM_RESOURCES_PATH + "comp.templ"))));

      template = template.replaceAll("%date%", LocaleManager.PARSER_SDF.format(new Date()));
      template = template.replaceAll("%comp_files%", compFiles.toString());

    } catch (IOException e) {
      logger.error("Could not read template : {}", e.getMessage());
      return;
    }

    PrintWriter writer;
    try {
      writer = new PrintWriter(VhdlSimConstants.SIM_PATH + "comp.tcl", StandardCharsets.UTF_8);
      writer.print(template);
      writer.close();
    } catch (IOException e) {
      logger.error("Could not create run.tcl file : {}", e.getMessage());
      e.printStackTrace();
      return;
    }

    valid = true;
  }
}
