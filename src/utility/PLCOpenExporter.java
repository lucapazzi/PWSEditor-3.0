package utility;

import assembly.Action;
import assembly.ActionList;
import assembly.Assembly;
import machinery.State;
import machinery.StateInterface;
import machinery.StateMachine;
import machinery.Transition;
import machinery.TransitionInterface;
import pws.PWSState;
import pws.PWSStateMachine;
import pws.PWSTransition;
import smalgebra.AndProposition;
import smalgebra.BasicStateProposition;
import smalgebra.FalseProposition;
import smalgebra.NotProposition;
import smalgebra.OrProposition;
import smalgebra.SMProposition;
import smalgebra.TrueProposition;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Exports a PWS controller and its simple assembly machines as PLCopen XML. */
public final class PLCOpenExporter {
    private static final Pattern IDENTIFIER_BODY = Pattern.compile("[^A-Za-z0-9_]");
    private static final String INITIAL_STATE_NAME = "Init";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private PLCOpenExporter() {
    }

    /** Generates PLCopen XML for the provided controller and its simple assembly machines. */
    public static String generate(PWSStateMachine controller) {
        return generate(controller, null);
    }

    /** Generates PLCopen XML for the provided controller using a preferred controller FB name. */
    public static String generate(PWSStateMachine controller, String preferredControllerName) {
        return new BundleGenerator(controller, preferredControllerName).generate();
    }

    /** Writes PLCopen XML for the provided controller to the given file. */
    public static void exportToFile(PWSStateMachine controller, File file) throws IOException {
        exportToFile(controller, file, null);
    }

    /** Writes PLCopen XML for the provided controller using a preferred controller FB name. */
    public static void exportToFile(PWSStateMachine controller, File file, String preferredControllerName) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Target file cannot be null.");
        }
        Files.writeString(file.toPath(), generate(controller, preferredControllerName), StandardCharsets.UTF_8);
    }

    private static final class BundleGenerator {
        private final PWSStateMachine controller;
        private final String preferredControllerName;
        private final LinkedHashMap<String, String> dataTypesByName = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> pousByName = new LinkedHashMap<>();
        private final List<String> orderedDataTypes = new ArrayList<>();
        private final List<String> orderedPous = new ArrayList<>();
        private final LinkedHashMap<String, BasicMachineGenerator> basicGeneratorsByMachineId = new LinkedHashMap<>();
        private ControllerGenerator controllerGenerator;

        private BundleGenerator(PWSStateMachine controller, String preferredControllerName) {
            if (controller == null) {
                throw new IllegalArgumentException("Controller cannot be null.");
            }
            this.controller = controller;
            this.preferredControllerName = preferredControllerName;
        }

        private String generate() {
            appendSimpleAssemblyMachines();
            appendController();
            appendProgram();

            String projectName = sanitizeIdentifier(chooseControllerName(controller, preferredControllerName), "ControllerProject");
            String creationDateTime = DATE_TIME_FORMAT.format(LocalDateTime.now().withNano(0));

            StringBuilder out = new StringBuilder();
            out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            out.append("<project xmlns=\"http://www.plcopen.org/xml/tc6_0201\"\n");
            out.append("         xmlns:xhtml=\"http://www.w3.org/1999/xhtml\"\n");
            out.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
            out.append("         xsi:schemaLocation=\"http://www.plcopen.org/xml/tc6_0201 http://www.plcopen.org/xml/tc6_0201\">\n");
            out.append("  <fileHeader companyName=\"PWSEditor\"\n");
            out.append("              productName=\"PWSEditor\"\n");
            out.append("              productVersion=\"local\"\n");
            out.append("              creationDateTime=\"").append(escapeAttribute(creationDateTime)).append("\"/>\n");
            out.append("  <contentHeader name=\"").append(escapeAttribute(projectName)).append("\">\n");
            out.append("    <coordinateInfo>\n");
            out.append("      <fbd><scaling x=\"8\" y=\"8\"/></fbd>\n");
            out.append("      <ld><scaling x=\"8\" y=\"8\"/></ld>\n");
            out.append("      <sfc><scaling x=\"8\" y=\"8\"/></sfc>\n");
            out.append("    </coordinateInfo>\n");
            out.append("  </contentHeader>\n");
            out.append("  <types>\n");
            out.append("    <dataTypes>\n");
            for (String dataType : orderedDataTypes) {
                appendIndentedBlock(out, dataType, 6);
            }
            out.append("    </dataTypes>\n");
            out.append("    <pous>\n");
            for (String pou : orderedPous) {
                appendIndentedBlock(out, pou, 6);
            }
            out.append("    </pous>\n");
            out.append("  </types>\n");
            out.append("  <instances>\n");
            out.append("    <configurations/>\n");
            out.append("  </instances>\n");
            out.append("</project>\n");
            return out.toString();
        }

        private void appendSimpleAssemblyMachines() {
            Assembly assembly = controller.getAssembly();
            if (assembly == null || assembly.getStateMachines() == null) {
                return;
            }
            for (Map.Entry<String, StateMachine> entry : assembly.getStateMachines().entrySet()) {
                String machineId = entry.getKey();
                StateMachine machine = entry.getValue();
                if (machine == null) {
                    continue;
                }
                if (machine instanceof PWSStateMachine) {
                    throw new IllegalArgumentException(
                            "Assembly machine '" + machineId + "' is not a simple machine and cannot be exported in this mode.");
                }
                BasicMachineGenerator generator = new BasicMachineGenerator(machine, machineId);
                basicGeneratorsByMachineId.put(machineId, generator);
                registerDataType(generator.getEnumTypeName(), generator.generateDataTypeXml());
                registerPou(generator.getFunctionBlockName(), generator.generatePouXml());
            }
        }

        private void appendController() {
            controllerGenerator = new ControllerGenerator(controller, preferredControllerName);
            registerDataType(controllerGenerator.getEnumTypeName(), controllerGenerator.generateDataTypeXml());
            registerPou(controllerGenerator.getFunctionBlockName(), controllerGenerator.generatePouXml());
        }

        private void appendProgram() {
            if (controllerGenerator == null) {
                throw new IllegalStateException("Controller export must be generated before PLC_PRG.");
            }
            ProgramGenerator generator = new ProgramGenerator(controllerGenerator, basicGeneratorsByMachineId);
            registerPou(generator.getProgramName(), generator.generatePouXml());
        }

        private void registerDataType(String name, String body) {
            String existing = dataTypesByName.get(name);
            if (existing == null) {
                dataTypesByName.put(name, body);
                orderedDataTypes.add(body);
                return;
            }
            if (!existing.equals(body)) {
                throw new IllegalArgumentException(
                        "Multiple exported data types resolve to the same PLCopen name '" + name
                                + "' but have different contents.");
            }
        }

        private void registerPou(String name, String body) {
            String existing = pousByName.get(name);
            if (existing == null) {
                pousByName.put(name, body);
                orderedPous.add(body);
                return;
            }
            if (!existing.equals(body)) {
                throw new IllegalArgumentException(
                        "Multiple exported function blocks resolve to the same PLCopen name '" + name
                                + "' but have different contents.");
            }
        }
    }

    private abstract static class BaseGenerator {
        protected final String functionBlockName;
        protected final String enumTypeName;

        protected BaseGenerator(String functionBlockName, String enumTypeName) {
            this.functionBlockName = functionBlockName;
            this.enumTypeName = enumTypeName;
        }

        protected String buildEnumDataTypeXml(List<String> valueNames) {
            StringBuilder out = new StringBuilder();
            out.append("<dataType name=\"").append(escapeAttribute(enumTypeName)).append("\">\n");
            out.append("  <baseType>\n");
            out.append("    <enum>\n");
            out.append("      <values>\n");
            for (String valueName : valueNames) {
                out.append("        <value name=\"").append(escapeAttribute(valueName)).append("\"/>\n");
            }
            out.append("      </values>\n");
            out.append("    </enum>\n");
            out.append("  </baseType>\n");
            out.append("</dataType>");
            return out.toString();
        }

        protected String buildPouXml(List<VariableDecl> inOutVars,
                                     List<VariableDecl> inputVars,
                                     List<VariableDecl> outputVars,
                                     List<VariableDecl> localVars,
                                     String bodySt) {
            return buildPouXml(functionBlockName, "functionBlock", inOutVars, inputVars, outputVars, localVars, bodySt);
        }

        protected String buildProgramPouXml(String programName,
                                            List<VariableDecl> localVars,
                                            String bodySt) {
            return buildPouXml(programName, "program", List.of(), List.of(), List.of(), localVars, bodySt);
        }

        private String buildPouXml(String pouName,
                                   String pouType,
                                   List<VariableDecl> inOutVars,
                                   List<VariableDecl> inputVars,
                                   List<VariableDecl> outputVars,
                                   List<VariableDecl> localVars,
                                   String bodySt) {
            StringBuilder out = new StringBuilder();
            out.append("<pou name=\"").append(escapeAttribute(pouName)).append("\" pouType=\"")
                    .append(escapeAttribute(pouType)).append("\">\n");
            out.append("  <interface>\n");
            appendVariableBlock(out, "inOutVars", inOutVars);
            appendVariableBlock(out, "inputVars", inputVars);
            appendVariableBlock(out, "outputVars", outputVars);
            appendVariableBlock(out, "localVars", localVars);
            out.append("  </interface>\n");
            out.append("  <body>\n");
            out.append("    <ST>\n");
            appendStructuredTextBody(out, bodySt);
            out.append("    </ST>\n");
            out.append("  </body>\n");
            out.append("</pou>");
            return out.toString();
        }

        private void appendVariableBlock(StringBuilder out, String tagName, List<VariableDecl> vars) {
            if (vars == null || vars.isEmpty()) {
                return;
            }
            out.append("    <").append(tagName).append(">\n");
            for (VariableDecl var : vars) {
                appendVariable(out, var);
            }
            out.append("    </").append(tagName).append(">\n");
        }

        private void appendVariable(StringBuilder out, VariableDecl var) {
            out.append("      <variable name=\"").append(escapeAttribute(var.name())).append("\">\n");
            out.append("        <type>\n");
            appendType(out, var.typeName(), var.derivedType());
            out.append("        </type>\n");
            if (var.initialValue() != null) {
                out.append("        <initialValue>\n");
                out.append("          <simpleValue value=\"")
                        .append(escapeAttribute(var.initialValue()))
                        .append("\"/>\n");
                out.append("        </initialValue>\n");
            }
            if (var.comment() != null && !var.comment().isBlank()) {
                out.append("        <documentation>\n");
                out.append("          <xhtml:p>")
                        .append(escapeText(var.comment()))
                        .append("</xhtml:p>\n");
                out.append("        </documentation>\n");
            }
            out.append("      </variable>\n");
        }

        private void appendType(StringBuilder out, String typeName, boolean derived) {
            if (derived) {
                out.append("          <derived name=\"")
                        .append(escapeAttribute(typeName))
                        .append("\"/>\n");
            } else {
                out.append("          <").append(typeName).append("/>\n");
            }
        }

        private void appendStructuredTextBody(StringBuilder out, String stBody) {
            String normalized = stBody == null ? "" : stBody.replace("\r\n", "\n").replace('\r', '\n');
            out.append("      <xhtml:p><![CDATA[")
                    .append(toXmlCData(normalized))
                    .append("]]></xhtml:p>\n");
        }
    }

    private static final class ProgramGenerator extends BaseGenerator {
        private static final String PROGRAM_NAME = "PLC_PRG";

        private final ControllerGenerator controllerGenerator;
        private final LinkedHashMap<String, BasicMachineGenerator> basicGeneratorsByMachineId;
        private final String controllerInstanceName;

        private ProgramGenerator(ControllerGenerator controllerGenerator,
                                 LinkedHashMap<String, BasicMachineGenerator> basicGeneratorsByMachineId) {
            super(PROGRAM_NAME, "");
            this.controllerGenerator = controllerGenerator;
            this.basicGeneratorsByMachineId = basicGeneratorsByMachineId;

            Set<String> usedIdentifiers = new LinkedHashSet<>();
            usedIdentifiers.addAll(controllerGenerator.getMachineInstanceNames().values());
            this.controllerInstanceName = uniqueIdentifier(
                    sanitizeIdentifier(controllerGenerator.getFunctionBlockName() + "_inst", "controller_inst"),
                    usedIdentifiers
            );
        }

        private String getProgramName() {
            return PROGRAM_NAME;
        }

        private String generatePouXml() {
            List<VariableDecl> localVars = new ArrayList<>();
            for (Map.Entry<String, String> entry : controllerGenerator.getMachineInstanceNames().entrySet()) {
                BasicMachineGenerator generator = basicGeneratorsByMachineId.get(entry.getKey());
                if (generator == null) {
                    throw new IllegalArgumentException(
                            "Missing function block generator for assembly machine '" + entry.getKey() + "'.");
                }
                localVars.add(new VariableDecl(entry.getValue(), generator.getFunctionBlockName(), true, null, null));
            }
            localVars.add(new VariableDecl(
                    controllerInstanceName,
                    controllerGenerator.getFunctionBlockName(),
                    true,
                    null,
                    null
            ));
            return buildProgramPouXml(PROGRAM_NAME, localVars, buildBodyStructuredText());
        }

        private String buildBodyStructuredText() {
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, String> entry : controllerGenerator.getMachineInstanceNames().entrySet()) {
                String instanceName = entry.getValue();
                out.append(instanceName).append("();\n");
            }
            out.append(controllerInstanceName).append("(")
                    .append(String.join(", ", buildControllerCallArguments()))
                    .append(");\n");
            return out.toString().trim();
        }

        private List<String> buildControllerCallArguments() {
            List<String> arguments = new ArrayList<>();
            for (Map.Entry<String, String> entry : controllerGenerator.getMachineInstanceNames().entrySet()) {
                String formalName = controllerGenerator.getMachineFormalName(entry.getKey());
                if (formalName == null) {
                    throw new IllegalArgumentException(
                            "Missing formal IN_OUT name for assembly machine '" + entry.getKey() + "'.");
                }
                arguments.add(formalName + " := " + entry.getValue());
            }
            return arguments;
        }

    }

    private static final class ControllerGenerator extends BaseGenerator {
        private final PWSStateMachine controller;
        private final Assembly assembly;
        private final LinkedHashMap<String, StateMachine> assemblyMachines = new LinkedHashMap<>();
        private final List<PWSState> controllerStates = new ArrayList<>();
        private final List<PWSTransition> enabledTransitions = new ArrayList<>();
        private final LinkedHashMap<PWSState, List<PWSTransition>> outgoingByState = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> machineVarNames = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> machineTypeNames = new LinkedHashMap<>();
        private final LinkedHashMap<String, LinkedHashMap<String, String>> machineStateNames = new LinkedHashMap<>();
        private final LinkedHashMap<PWSState, String> controllerStateNames = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> triggerInputNames = new LinkedHashMap<>();
        private final LinkedHashMap<TransitionInterface, String> simulationInputNames = new LinkedHashMap<>();
        private final Set<String> usedIdentifiers = new LinkedHashSet<>();
        private final Set<String> usedTriggerIdentifiers = new LinkedHashSet<>();

        private final List<PWSTransition> initialTransitions = new ArrayList<>();
        private boolean usesTimer;

        private ControllerGenerator(PWSStateMachine controller, String preferredControllerName) {
            super(
                    sanitizeIdentifier(chooseControllerName(controller, preferredControllerName), "Controller"),
                    sanitizeIdentifier("Stati_" + sanitizeIdentifier(chooseControllerName(controller, preferredControllerName), "Controller"),
                            "Stati_Controller")
            );
            if (controller == null) {
                throw new IllegalArgumentException("Controller cannot be null.");
            }
            this.controller = controller;
            this.assembly = controller.getAssembly();
            if (assembly == null) {
                throw new IllegalArgumentException("Controller assembly is not available.");
            }
        }

        private String getFunctionBlockName() {
            return functionBlockName;
        }

        private String getEnumTypeName() {
            return enumTypeName;
        }

        private LinkedHashMap<String, String> getMachineInstanceNames() {
            LinkedHashMap<String, String> names = new LinkedHashMap<>();
            Set<String> used = new LinkedHashSet<>();
            for (String machineId : assemblyMachines.keySet()) {
                String formalName = machineVarNames.get(machineId);
                if (formalName == null) {
                    throw new IllegalArgumentException(
                            "Missing controller variable name for assembly machine '" + machineId + "'.");
                }
                names.put(machineId, uniqueIdentifier(formalName + "_inst", used));
            }
            return names;
        }

        private String getMachineFormalName(String machineId) {
            return machineVarNames.get(machineId);
        }

        private LinkedHashMap<String, String> getTriggerInputNames() {
            return new LinkedHashMap<>(triggerInputNames);
        }

        private String generateDataTypeXml() {
            collectModel();
            validateModel();
            prepareNames();

            List<String> values = new ArrayList<>();
            values.add(INITIAL_STATE_NAME);
            for (PWSState state : controllerStates) {
                values.add(controllerStateNames.get(state));
            }
            return buildEnumDataTypeXml(values);
        }

        private String generatePouXml() {
            if (controllerStateNames.isEmpty()) {
                collectModel();
                validateModel();
                prepareNames();
            }
            List<VariableDecl> inOutVars = new ArrayList<>();
            for (String machineId : assemblyMachines.keySet()) {
                inOutVars.add(new VariableDecl(
                        machineVarNames.get(machineId),
                        machineTypeNames.get(machineId),
                        true,
                        null,
                        null
                ));
            }

            List<VariableDecl> inputVars = new ArrayList<>();
            for (String trigger : triggerInputNames.keySet()) {
                inputVars.add(new VariableDecl(triggerInputNames.get(trigger), "BOOL", false, "FALSE", null));
            }

            List<VariableDecl> outputVars = List.of(new VariableDecl(
                    "stato",
                    enumTypeName,
                    true,
                    enumTypeName + "." + INITIAL_STATE_NAME,
                    null
            ));

            List<VariableDecl> localVars = new ArrayList<>();
            if (usesTimer) {
                localVars.add(new VariableDecl("timer", "TON", true, null, null));
            }

            return buildPouXml(inOutVars, inputVars, outputVars, localVars, buildBodyStructuredText());
        }

        private void collectModel() {
            if (!assemblyMachines.isEmpty() || !controllerStates.isEmpty() || !enabledTransitions.isEmpty()) {
                return;
            }
            Map<String, StateMachine> machines = assembly.getStateMachines();
            if (machines != null) {
                for (Map.Entry<String, StateMachine> entry : machines.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        assemblyMachines.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            for (StateInterface state : controller.getStates()) {
                if (state instanceof PWSState ps && !ps.isPseudoState()) {
                    controllerStates.add(ps);
                    outgoingByState.put(ps, new ArrayList<>());
                }
            }

            for (TransitionInterface transition : controller.getTransitions()) {
                if (!(transition instanceof PWSTransition pt) || !isTransitionEnabled(pt)) {
                    continue;
                }
                enabledTransitions.add(pt);
                if (isControllerInitialTransition(pt)) {
                    initialTransitions.add(pt);
                    continue;
                }
                if (pt.getSource() instanceof PWSState source && !source.isPseudoState()) {
                    outgoingByState.computeIfAbsent(source, ignored -> new ArrayList<>()).add(pt);
                }
            }
        }

        private void validateModel() {
            if (controllerStates.isEmpty()) {
                throw new IllegalArgumentException("The controller has no logical states to export.");
            }
            if (initialTransitions.isEmpty()) {
                throw new IllegalArgumentException("PLCopen export requires at least one enabled initial transition.");
            }
            for (StateMachine machine : assemblyMachines.values()) {
                if (machine instanceof PWSStateMachine) {
                    throw new IllegalArgumentException(
                            "Assembly machines for this export must be simple state machines.");
                }
            }
            for (PWSState state : controllerStates) {
                List<PWSTransition> timeoutTransitions = getEnabledTimeoutTransitions(state);
                if (state.isTimedState()) {
                    if (timeoutTransitions.size() != 1) {
                        throw new IllegalArgumentException(
                                "Timed state '" + state.getName() + "' must have exactly one enabled timeout transition.");
                    }
                    toStructuredTextTimeLiteral(state.getTimeoutLabel());
                    usesTimer = true;
                } else if (!timeoutTransitions.isEmpty()) {
                    throw new IllegalArgumentException(
                            "State '" + state.getName() + "' has a timeout transition but is not marked as timed.");
                }
            }

            collectTriggerInputs();
            validateTransitionTargets();
        }

        private void prepareNames() {
            if (!machineVarNames.isEmpty() || !controllerStateNames.isEmpty()) {
                return;
            }
            for (Map.Entry<String, StateMachine> entry : assemblyMachines.entrySet()) {
                String machineId = entry.getKey();
                StateMachine machine = entry.getValue();
                machineVarNames.put(machineId, uniqueIdentifier(machineId, usedIdentifiers));
                machineTypeNames.put(machineId, sanitizeIdentifier(machine.getName(), machineId));
                machineStateNames.put(machineId, buildUniqueStateNames(machine.getLogicalStates()));
            }

            for (PWSState state : controllerStates) {
                controllerStateNames.put(state, uniqueIdentifier(state.getName(), usedIdentifiers));
            }
        }

        private void collectTriggerInputs() {
            Set<String> usedTriggerNames = new LinkedHashSet<>();
            for (PWSTransition transition : enabledTransitions) {
                if (!transition.isTriggerable() || isControllerInitialTransition(transition)) {
                    continue;
                }
                String trigger = transition.getTriggerEvent();
                if (trigger == null || trigger.isBlank()) {
                    continue;
                }
                if (usedTriggerNames.add(trigger)) {
                    triggerInputNames.put(trigger, uniqueIdentifier(toEventVariableName(trigger), usedTriggerIdentifiers));
                }
            }
        }

        private void validateTransitionTargets() {
            for (PWSTransition transition : enabledTransitions) {
                if (transition.getTarget() == null || transition.getSource() == null) {
                    throw new IllegalArgumentException("Every exported transition must have both source and target states.");
                }
                if (transition.getTarget() instanceof PWSState target && target.isPseudoState()) {
                    throw new IllegalArgumentException("Transitions toward the pseudostate are not supported in PLCopen export.");
                }
                validateGuard(transition.getGuardProposition());
                validateActionList(transition.getActionList());
            }
        }

        private void validateGuard(SMProposition proposition) {
            if (proposition == null
                    || proposition instanceof TrueProposition
                    || proposition instanceof FalseProposition) {
                return;
            }
            if (proposition instanceof BasicStateProposition basic) {
                String machineId = basic.getMachineId();
                StateMachine machine = assemblyMachines.get(machineId);
                if (machine == null) {
                    throw new IllegalArgumentException(
                            "Guard references unknown assembly machine '" + machineId + "'.");
                }
                boolean stateExists = false;
                for (StateInterface state : machine.getLogicalStates()) {
                    if (Objects.equals(state.getName(), basic.getStateName())) {
                        stateExists = true;
                        break;
                    }
                }
                if (!stateExists) {
                    throw new IllegalArgumentException(
                            "Guard references unknown state '" + basic.getStateName() + "' on machine '" + machineId + "'.");
                }
                return;
            }
            if (proposition instanceof AndProposition andProp) {
                validateGuard(andProp.getLeft());
                validateGuard(andProp.getRight());
                return;
            }
            if (proposition instanceof OrProposition orProp) {
                validateGuard(orProp.getLeft());
                validateGuard(orProp.getRight());
                return;
            }
            if (proposition instanceof NotProposition notProp) {
                validateGuard(notProp.getProposition());
                return;
            }
            throw new IllegalArgumentException(
                    "Unsupported guard proposition for PLCopen export: " + proposition.getClass().getSimpleName());
        }

        private void validateActionList(ActionList actions) {
            if (actions == null) {
                return;
            }
            for (Action action : actions) {
                if (action == null) {
                    continue;
                }
                if (!assemblyMachines.containsKey(action.getMachineId())) {
                    throw new IllegalArgumentException(
                            "Action references unknown assembly machine '" + action.getMachineId() + "'.");
                }
                if (action.getEvent() == null || action.getEvent().isBlank()) {
                    throw new IllegalArgumentException("Action event name cannot be blank.");
                }
            }
        }

        private String buildBodyStructuredText() {
            StringBuilder out = new StringBuilder();
            out.append("CASE stato OF\n");
            appendInitialBranch(out);
            for (PWSState state : controllerStates) {
                appendStateBranch(out, state);
            }
            out.append("END_CASE");
            return out.toString();
        }

        private void appendInitialBranch(StringBuilder out) {
            out.append("    ").append(enumTypeName).append(".").append(INITIAL_STATE_NAME).append(":\n");
            for (PWSTransition transition : initialTransitions) {
                appendConditionalTransition(out, toCondition(transition), transition, false, "        ", "            ");
            }
            out.append("\n");
        }

        private void appendStateBranch(StringBuilder out, PWSState state) {
            String stateName = controllerStateNames.get(state);
            out.append("    ").append(enumTypeName).append(".").append(stateName).append(":\n");
            if (state.isTimedState()) {
                out.append("        timer(IN := TRUE, PT := ")
                        .append(toStructuredTextTimeLiteral(state.getTimeoutLabel()))
                        .append(");\n");
            }
            List<PWSTransition> outgoing = outgoingByState.get(state);
            if (outgoing == null || outgoing.isEmpty()) {
                out.append("\n");
                return;
            }
            for (PWSTransition transition : outgoing) {
                appendConditionalTransition(out, toCondition(transition), transition, state.isTimedState(), "        ", "            ");
            }
            out.append("\n");
        }

        private void appendConditionalTransition(StringBuilder out,
                                                 String condition,
                                                 PWSTransition transition,
                                                 boolean resetTimerAfterTransition,
                                                 String baseIndent,
                                                 String bodyIndent) {
            if ("TRUE".equals(condition)) {
                appendTransitionBody(out, transition, resetTimerAfterTransition, baseIndent);
                return;
            }
            out.append(baseIndent).append("IF ").append(condition).append(" THEN\n");
            appendTransitionBody(out, transition, resetTimerAfterTransition, bodyIndent);
            out.append(baseIndent).append("END_IF;\n");
        }

        private void appendTransitionBody(StringBuilder out,
                                          PWSTransition transition,
                                          boolean resetTimerAfterTransition,
                                          String indent) {
            ActionList actions = transition.getActionList();
            if (actions != null) {
                for (Action action : actions) {
                    if (action == null) {
                        continue;
                    }
                    out.append(indent).append(toActionReference(action.getMachineId(), action.getEvent()))
                            .append(" := TRUE;\n");
                }
            }
            String targetName = toControllerStateName(transition.getTarget());
            out.append(indent).append("stato := ")
                    .append(enumTypeName).append(".").append(targetName).append(";\n");
            if (resetTimerAfterTransition) {
                out.append(indent).append("timer(IN := FALSE);\n");
            }
            String consumedTrigger = toConsumedTriggerReference(transition);
            if (consumedTrigger != null) {
                out.append(indent).append(consumedTrigger).append(" := FALSE;\n");
            }
        }

        private String toCondition(PWSTransition transition) {
            if (isControllerInitialTransition(transition)) {
                String guard = toGuardCondition(transition.getGuardProposition());
                if (isTrueGuard(transition.getGuardProposition())) {
                    return "TRUE";
                }
                return guard;
            }
            if (transition.isTimeoutTransition()) {
                return "timer.Q";
            }

            String guard = toGuardCondition(transition.getGuardProposition());
            if (transition.isTriggerable()) {
                String trigger = triggerInputNames.get(transition.getTriggerEvent());
                if (trigger == null) {
                    throw new IllegalArgumentException(
                            "Missing PLCopen input name for trigger '" + transition.getTriggerEvent() + "'.");
                }
                if (isTrueGuard(transition.getGuardProposition())) {
                    return trigger;
                }
                return trigger + " AND (" + guard + ")";
            }
            return guard;
        }

        private String toGuardCondition(SMProposition proposition) {
            if (proposition == null || proposition instanceof TrueProposition) {
                return "TRUE";
            }
            if (proposition instanceof FalseProposition) {
                return "FALSE";
            }
            if (proposition instanceof BasicStateProposition basic) {
                String machineVar = machineVarNames.get(basic.getMachineId());
                String typeName = machineTypeNames.get(basic.getMachineId());
                Map<String, String> stateNames = machineStateNames.get(basic.getMachineId());
                if (machineVar == null || typeName == null || stateNames == null) {
                    throw new IllegalArgumentException(
                            "Cannot export guard for unknown machine '" + basic.getMachineId() + "'.");
                }
                String stateName = stateNames.get(basic.getStateName());
                if (stateName == null) {
                    throw new IllegalArgumentException(
                            "Cannot export guard for unknown state '" + basic.getStateName()
                                    + "' on machine '" + basic.getMachineId() + "'.");
                }
                return machineVar + ".stato = Stati_" + typeName + "." + stateName;
            }
            if (proposition instanceof AndProposition andProp) {
                return "(" + toGuardCondition(andProp.getLeft()) + " AND "
                        + toGuardCondition(andProp.getRight()) + ")";
            }
            if (proposition instanceof OrProposition orProp) {
                return "(" + toGuardCondition(orProp.getLeft()) + " OR "
                        + toGuardCondition(orProp.getRight()) + ")";
            }
            if (proposition instanceof NotProposition notProp) {
                return "(NOT " + toGuardCondition(notProp.getProposition()) + ")";
            }
            throw new IllegalArgumentException(
                    "Unsupported guard proposition for PLCopen export: " + proposition.getClass().getSimpleName());
        }

        private boolean isTrueGuard(SMProposition proposition) {
            return proposition == null || proposition instanceof TrueProposition;
        }

        private String toActionReference(String machineId, String event) {
            String machineVar = machineVarNames.get(machineId);
            if (machineVar == null) {
                throw new IllegalArgumentException(
                        "Cannot export action for unknown machine '" + machineId + "'.");
            }
            return machineVar + "." + toEventVariableName(event);
        }

        private String toControllerStateName(StateInterface state) {
            if (!(state instanceof PWSState ps) || ps.isPseudoState()) {
                throw new IllegalArgumentException("Controller target state is not a logical PWS state.");
            }
            String mapped = controllerStateNames.get(ps);
            if (mapped == null) {
                throw new IllegalArgumentException("Missing PLCopen state name for '" + ps.getName() + "'.");
            }
            return mapped;
        }

        private String toConsumedTriggerReference(PWSTransition transition) {
            if (transition == null || isControllerInitialTransition(transition) || !transition.isTriggerable()) {
                return null;
            }
            String trigger = triggerInputNames.get(transition.getTriggerEvent());
            if (trigger == null || trigger.isBlank()) {
                return null;
            }
            return trigger;
        }

        private boolean isControllerInitialTransition(PWSTransition transition) {
            if (transition == null) {
                return false;
            }
            if (transition.isInitialTransition()) {
                return true;
            }
            if (transition.getSource() instanceof PWSState source && source.isPseudoState()) {
                return true;
            }
            return "_init".equals(transition.getTriggerEvent());
        }

        private List<PWSTransition> getEnabledTimeoutTransitions(PWSState state) {
            List<PWSTransition> timeoutTransitions = new ArrayList<>();
            List<PWSTransition> outgoing = outgoingByState.get(state);
            if (outgoing == null) {
                return timeoutTransitions;
            }
            for (PWSTransition transition : outgoing) {
                if (transition.isTimeoutTransition()) {
                    timeoutTransitions.add(transition);
                }
            }
            return timeoutTransitions;
        }
    }

    private static final class BasicMachineGenerator extends BaseGenerator {
        private final StateMachine machine;
        private final List<StateInterface> logicalStates = new ArrayList<>();
        private final List<TransitionInterface> enabledTransitions = new ArrayList<>();
        private final LinkedHashMap<StateInterface, List<TransitionInterface>> outgoingByState = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> triggerInputNames = new LinkedHashMap<>();
        private final LinkedHashMap<TransitionInterface, String> simulationInputNames = new LinkedHashMap<>();
        private final LinkedHashMap<StateInterface, String> stateNames = new LinkedHashMap<>();
        private final Set<String> usedIdentifiers = new LinkedHashSet<>();
        private final Set<String> usedTriggerIdentifiers = new LinkedHashSet<>();

        private final List<TransitionInterface> initialTransitions = new ArrayList<>();
        private boolean usesTimer;

        private BasicMachineGenerator(StateMachine machine, String fallbackName) {
            super(
                    sanitizeIdentifier(machine != null ? machine.getName() : null, fallbackName),
                    sanitizeIdentifier("Stati_" + sanitizeIdentifier(machine != null ? machine.getName() : null, fallbackName),
                            "Stati_Machine")
            );
            if (machine == null) {
                throw new IllegalArgumentException("Assembly machine cannot be null.");
            }
            this.machine = machine;
        }

        private String getFunctionBlockName() {
            return functionBlockName;
        }

        private String getEnumTypeName() {
            return enumTypeName;
        }

        private LinkedHashMap<String, String> getTriggerInputNames() {
            return new LinkedHashMap<>(triggerInputNames);
        }

        private String generateDataTypeXml() {
            collectModel();
            validateModel();
            prepareNames();

            List<String> values = new ArrayList<>();
            values.add(INITIAL_STATE_NAME);
            for (StateInterface state : logicalStates) {
                values.add(stateNames.get(state));
            }
            return buildEnumDataTypeXml(values);
        }

        private String generatePouXml() {
            if (stateNames.isEmpty()) {
                collectModel();
                validateModel();
                prepareNames();
            }
            List<VariableDecl> inputVars = new ArrayList<>();
            for (String trigger : triggerInputNames.keySet()) {
                inputVars.add(new VariableDecl(triggerInputNames.get(trigger), "BOOL", false, "FALSE", null));
            }
            for (Map.Entry<TransitionInterface, String> entry : simulationInputNames.entrySet()) {
                TransitionInterface transition = entry.getKey();
                inputVars.add(new VariableDecl(
                        entry.getValue(),
                        "BOOL",
                        false,
                        "FALSE",
                        "simulation event from " + transition.getSource().getName() + " to " + transition.getTarget().getName()
                ));
            }

            List<VariableDecl> outputVars = List.of(new VariableDecl(
                    "stato",
                    enumTypeName,
                    true,
                    enumTypeName + "." + INITIAL_STATE_NAME,
                    null
            ));

            List<VariableDecl> localVars = new ArrayList<>();
            if (usesTimer) {
                localVars.add(new VariableDecl("timer", "TON", true, null, null));
            }

            return buildPouXml(List.of(), inputVars, outputVars, localVars, buildBodyStructuredText());
        }

        private void collectModel() {
            if (!logicalStates.isEmpty() || !enabledTransitions.isEmpty()) {
                return;
            }
            for (StateInterface state : machine.getStates()) {
                if (state == null) {
                    continue;
                }
                if (state == machine.getPseudoState() || "PseudoState".equals(state.getName())) {
                    continue;
                }
                logicalStates.add(state);
                outgoingByState.put(state, new ArrayList<>());
            }

            for (TransitionInterface transition : machine.getTransitions()) {
                if (!isTransitionEnabled(transition)) {
                    continue;
                }
                enabledTransitions.add(transition);
                if (isInitialTransition(machine, transition)) {
                    initialTransitions.add(transition);
                    continue;
                }
                StateInterface source = transition.getSource();
                if (source != null && outgoingByState.containsKey(source)) {
                    outgoingByState.get(source).add(transition);
                }
            }
        }

        private void validateModel() {
            if (logicalStates.isEmpty()) {
                throw new IllegalArgumentException(
                        "Machine '" + machine.getName() + "' has no logical states to export.");
            }
            if (initialTransitions.isEmpty()) {
                throw new IllegalArgumentException(
                        "Machine '" + machine.getName() + "' requires one enabled initial transition.");
            }
            collectTriggerInputs();
            for (StateInterface state : logicalStates) {
                if (!(state instanceof State concreteState)) {
                    continue;
                }
                List<TransitionInterface> timeoutTransitions = getEnabledTimeoutTransitions(concreteState);
                if (concreteState.isTimedState()) {
                    if (timeoutTransitions.size() != 1) {
                        throw new IllegalArgumentException(
                                "Timed state '" + concreteState.getName()
                                        + "' on machine '" + machine.getName()
                                        + "' must have exactly one enabled timeout transition.");
                    }
                    toStructuredTextTimeLiteral(concreteState.getTimeoutLabel());
                    usesTimer = true;
                } else if (!timeoutTransitions.isEmpty()) {
                    throw new IllegalArgumentException(
                            "State '" + concreteState.getName()
                                    + "' on machine '" + machine.getName()
                                    + "' has a timeout transition but is not marked as timed.");
                }
            }
            for (TransitionInterface transition : enabledTransitions) {
                if (transition.getSource() == null || transition.getTarget() == null) {
                    throw new IllegalArgumentException(
                            "Every exported transition on machine '" + machine.getName() + "' must have source and target.");
                }
                if (transition.getTarget() == machine.getPseudoState()
                        || "PseudoState".equals(transition.getTarget().getName())) {
                    throw new IllegalArgumentException(
                            "Machine '" + machine.getName() + "' contains a transition toward the pseudostate.");
                }
            }
        }

        private void prepareNames() {
            if (!stateNames.isEmpty()) {
                return;
            }
            for (StateInterface state : logicalStates) {
                stateNames.put(state, uniqueIdentifier(state.getName(), usedIdentifiers));
            }
        }

        private void collectTriggerInputs() {
            Set<String> usedTriggerNames = new LinkedHashSet<>();
            for (TransitionInterface transition : enabledTransitions) {
                if (isSimulationInitialTransition(transition)) {
                    simulationInputNames.put(transition, buildSimulationEventName(transition));
                    continue;
                }
                if (isInitialTransition(machine, transition)) {
                    continue;
                }
                if (isSimulationAutonomousTransition(transition)) {
                    simulationInputNames.put(transition, buildSimulationEventName(transition));
                    continue;
                }
                if (!transition.isTriggerable()) {
                    continue;
                }
                String trigger = transition.getTriggerEvent();
                if (trigger == null || trigger.isBlank()) {
                    continue;
                }
                if (usedTriggerNames.add(trigger)) {
                    triggerInputNames.put(trigger, uniqueIdentifier(toEventVariableName(trigger), usedTriggerIdentifiers));
                }
            }
        }

        private String buildBodyStructuredText() {
            StringBuilder out = new StringBuilder();
            out.append("CASE stato OF\n");
            appendInitialBranch(out);
            for (StateInterface state : logicalStates) {
                appendStateBranch(out, state);
            }
            out.append("END_CASE");
            return out.toString();
        }

        private void appendInitialBranch(StringBuilder out) {
            out.append("    ").append(enumTypeName).append(".").append(INITIAL_STATE_NAME).append(":\n");
            for (TransitionInterface transition : initialTransitions) {
                appendConditionalTransition(
                        out,
                        toInitialCondition(transition),
                        transition,
                        false,
                        "        ",
                        "            "
                );
            }
            out.append("\n");
        }

        private void appendStateBranch(StringBuilder out, StateInterface state) {
            String stateName = stateNames.get(state);
            out.append("    ").append(enumTypeName).append(".").append(stateName).append(":\n");
            if (state instanceof State concreteState && concreteState.isTimedState()) {
                out.append("        timer(IN := TRUE, PT := ")
                        .append(toStructuredTextTimeLiteral(concreteState.getTimeoutLabel()))
                        .append(");\n");
            }
            List<TransitionInterface> outgoing = outgoingByState.get(state);
            if (outgoing == null || outgoing.isEmpty()) {
                out.append("\n");
                return;
            }
            for (TransitionInterface transition : outgoing) {
                appendConditionalTransition(
                        out,
                        toCondition(transition),
                        transition,
                        state instanceof State concreteState && concreteState.isTimedState(),
                        "        ",
                        "            "
                );
            }
            out.append("\n");
        }

        private void appendConditionalTransition(StringBuilder out,
                                                 String condition,
                                                 TransitionInterface transition,
                                                 boolean resetTimerAfterTransition,
                                                 String baseIndent,
                                                 String bodyIndent) {
            if ("TRUE".equals(condition)) {
                appendTransitionBody(out, transition, resetTimerAfterTransition, baseIndent);
                return;
            }
            out.append(baseIndent).append("IF ").append(condition).append(" THEN\n");
            appendTransitionBody(out, transition, resetTimerAfterTransition, bodyIndent);
            out.append(baseIndent).append("END_IF;\n");
        }

        private void appendTransitionBody(StringBuilder out,
                                          TransitionInterface transition,
                                          boolean resetTimerAfterTransition,
                                          String indent) {
            String targetName = stateNames.get(transition.getTarget());
            if (targetName == null) {
                throw new IllegalArgumentException(
                        "Missing PLCopen state name for target '" + transition.getTarget().getName() + "'.");
            }
            out.append(indent).append("stato := ")
                    .append(enumTypeName).append(".").append(targetName).append(";\n");
            if (resetTimerAfterTransition) {
                out.append(indent).append("timer(IN := FALSE);\n");
            }
            String consumedTrigger = toConsumedTriggerReference(transition);
            if (consumedTrigger != null) {
                out.append(indent).append(consumedTrigger).append(" := FALSE;\n");
            }
        }

        private String toCondition(TransitionInterface transition) {
            if (transition instanceof Transition concrete && concrete.isTimeoutTransition()) {
                return "timer.Q";
            }
            if (isSimulationAutonomousTransition(transition)) {
                String simulationInput = simulationInputNames.get(transition);
                if (simulationInput == null) {
                    throw new IllegalArgumentException("Missing PLCopen simulation input name for autonomous transition.");
                }
                return simulationInput;
            }
            if (!transition.isTriggerable()) {
                return "TRUE";
            }
            String triggerName = triggerInputNames.get(transition.getTriggerEvent());
            if (triggerName == null) {
                throw new IllegalArgumentException(
                        "Missing PLCopen input name for trigger '" + transition.getTriggerEvent() + "'.");
            }
            return triggerName;
        }

        private String toInitialCondition(TransitionInterface transition) {
            if (isSimulationInitialTransition(transition)) {
                String simulationInput = simulationInputNames.get(transition);
                if (simulationInput == null) {
                    throw new IllegalArgumentException("Missing PLCopen simulation input name for initial transition.");
                }
                return simulationInput;
            }
            return "TRUE";
        }

        private String toConsumedTriggerReference(TransitionInterface transition) {
            if (transition == null) {
                return null;
            }
            if (isInitialTransition(machine, transition)) {
                return null;
            }
            if (isSimulationAutonomousTransition(transition)) {
                String simulationInput = simulationInputNames.get(transition);
                return simulationInput == null || simulationInput.isBlank() ? null : simulationInput;
            }
            if (!transition.isTriggerable()) {
                return null;
            }
            String triggerName = triggerInputNames.get(transition.getTriggerEvent());
            if (triggerName == null || triggerName.isBlank()) {
                return null;
            }
            return triggerName;
        }

        private boolean isSimulationAutonomousTransition(TransitionInterface transition) {
            if (transition == null || transition.isTriggerable()) {
                return false;
            }
            if (isInitialTransition(machine, transition)) {
                return false;
            }
            return !(transition instanceof Transition concrete) || !concrete.isTimeoutTransition();
        }

        private boolean isSimulationInitialTransition(TransitionInterface transition) {
            if (transition == null || !isInitialTransition(machine, transition)) {
                return false;
            }
            if (initialTransitions.size() <= 1) {
                return false;
            }
            return !transition.isTriggerable();
        }

        private String buildSimulationEventName(TransitionInterface transition) {
            String sourceName = isInitialTransition(machine, transition)
                    ? INITIAL_STATE_NAME
                    : transition.getSource() != null ? transition.getSource().getName() : "source";
            String targetName = transition.getTarget() != null ? transition.getTarget().getName() : "target";
            String base = "sim_event_" + sanitizeIdentifier(sourceName, "source") + "_" + sanitizeIdentifier(targetName, "target");
            return uniqueIdentifier(base, usedTriggerIdentifiers);
        }

        private List<TransitionInterface> getEnabledTimeoutTransitions(State state) {
            List<TransitionInterface> timeoutTransitions = new ArrayList<>();
            List<TransitionInterface> outgoing = outgoingByState.get(state);
            if (outgoing == null) {
                return timeoutTransitions;
            }
            for (TransitionInterface transition : outgoing) {
                if (transition instanceof Transition concrete && concrete.isTimeoutTransition()) {
                    timeoutTransitions.add(transition);
                }
            }
            return timeoutTransitions;
        }
    }

    private record VariableDecl(String name, String typeName, boolean derivedType, String initialValue, String comment) {
    }

    private static boolean isTransitionEnabled(TransitionInterface transition) {
        if (transition instanceof Transition concrete) {
            return concrete.isEnabled();
        }
        return true;
    }

    private static boolean isInitialTransition(StateMachine machine, TransitionInterface transition) {
        if (machine == null || transition == null) {
            return false;
        }
        StateInterface source = transition.getSource();
        if (source == null || source != machine.getPseudoState()) {
            return false;
        }
        String trigger = transition.getTriggerEvent();
        return transition.isAutonomous() || "_init".equals(trigger);
    }

    private static LinkedHashMap<String, String> buildUniqueStateNames(List<StateInterface> states) {
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>();
        if (states == null) {
            return names;
        }
        for (StateInterface state : states) {
            if (state == null) {
                continue;
            }
            names.put(state.getName(), uniqueIdentifier(state.getName(), used));
        }
        return names;
    }

    private static String toStructuredTextTimeLiteral(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Timed state label cannot be blank.");
        }
        String trimmed = label.trim();
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("T#")) {
            return "T#" + trimmed.substring(2);
        }
        return "T#" + trimmed;
    }

    private static String toEventVariableName(String event) {
        String sanitized = sanitizeIdentifier(event, "event");
        return sanitized.endsWith("_ev") ? sanitized : sanitized + "_ev";
    }

    private static String chooseControllerName(PWSStateMachine controller, String preferredControllerName) {
        String preferred = normalizeControllerName(preferredControllerName);
        if (preferred != null) {
            return preferred;
        }
        String current = normalizeControllerName(controller != null ? controller.getName() : null);
        if (current != null) {
            return current;
        }
        return "Controller";
    }

    private static String normalizeControllerName(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "Untitled".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static String sanitizeIdentifier(String raw, String fallback) {
        String value = (raw == null || raw.isBlank()) ? fallback : raw.trim();
        value = IDENTIFIER_BODY.matcher(value).replaceAll("_");
        if (value.isBlank()) {
            value = fallback;
        }
        if (!Character.isLetter(value.charAt(0)) && value.charAt(0) != '_') {
            value = "_" + value;
        }
        return value;
    }

    private static String uniqueIdentifier(String raw, Set<String> used) {
        String base = sanitizeIdentifier(raw, "id");
        String candidate = base;
        int suffix = 2;
        while (used.contains(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        used.add(candidate);
        return candidate;
    }

    private static void appendIndentedBlock(StringBuilder out, String block, int spaces) {
        String indent = " ".repeat(Math.max(spaces, 0));
        String[] lines = block.split("\n", -1);
        for (String line : lines) {
            if (!line.isEmpty()) {
                out.append(indent).append(line);
            }
            out.append('\n');
        }
    }

    private static String escapeAttribute(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapeText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String toXmlCData(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("]]>", "]]]]><![CDATA[>");
    }
}
