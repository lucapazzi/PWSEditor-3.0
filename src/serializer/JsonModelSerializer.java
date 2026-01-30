package serializer;

import assembly.Action;
import assembly.ActionList;
import assembly.Assembly;
import assembly.LTLFormula;
import assembly.MachineLibrary;
import machinery.State;
import machinery.StateInterface;
import machinery.StateMachine;
import machinery.Transition;
import machinery.TransitionInterface;
import pws.PWSState;
import pws.PWSStateMachine;
import pws.PWSTransition;
import pws.editor.PWSStateMachinePanel;
import pws.editor.semantics.Semantics;
import smalgebra.AndProposition;
import smalgebra.BasicStateProposition;
import smalgebra.FalseProposition;
import smalgebra.NotProposition;
import smalgebra.OrProposition;
import smalgebra.SMExpressionParser;
import smalgebra.SMProposition;
import smalgebra.TrueProposition;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** JSON-based serializer for PWS workspaces, machine libraries, and state machines. */
public final class JsonModelSerializer {
    public static final int FORMAT_VERSION = 1;

    private JsonModelSerializer() {
    }

    public static final class LoadedWorkspace {
        private final PWSStateMachine model;
        private final PWSStateMachinePanel.AnnotationData annotations;

        public LoadedWorkspace(PWSStateMachine model, PWSStateMachinePanel.AnnotationData annotations) {
            this.model = model;
            this.annotations = annotations;
        }

        public PWSStateMachine getModel() {
            return model;
        }

        public PWSStateMachinePanel.AnnotationData getAnnotations() {
            return annotations;
        }
    }

    public static void savePwsWorkspace(PWSStateMachine model,
                                        PWSStateMachinePanel.AnnotationData annotations,
                                        File file) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("type", "pws-workspace");
        root.put("controller", pwsControllerToMap(model));
        Assembly assembly = model.getAssembly();
        root.put("assembly", assemblyToMap(assembly));
        root.put("library", machineLibraryToMap(assembly.getMachineLibrary()));
        root.put("annotations", annotationDataToMap(annotations));
        JsonIO.writeFile(file, root);
    }

    public static LoadedWorkspace loadPwsWorkspace(File file) throws IOException {
        Object parsed = JsonIO.readFile(file);
        Map<String, Object> root = asMap(parsed, "workspace");
        Map<String, Object> assemblyMap = asMap(root.get("assembly"), "assembly");
        String assemblyId = getString(assemblyMap, "id", "PWSEditorAssembly");
        Assembly assembly = new Assembly(assemblyId);

        // Load library into the assembly's machine library.
        Map<String, Object> libraryMap = asMap(root.get("library"), "library");
        loadLibraryInto(libraryMap, assembly.getMachineLibrary());

        // Load assembly machines and formulas.
        loadAssemblyInto(assemblyMap, assembly);

        Map<String, Object> controllerMap = asMap(root.get("controller"), "controller");
        PWSStateMachine controller = pwsControllerFromMap(controllerMap, assembly);

        Map<String, Object> annMap = asMap(root.get("annotations"), "annotations");
        PWSStateMachinePanel.AnnotationData annotations = annotationDataFromMap(annMap);

        return new LoadedWorkspace(controller, annotations);
    }

    public static void saveStateMachine(StateMachine machine, File file) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("type", "state-machine");
        root.put("machine", basicStateMachineToMap(machine));
        JsonIO.writeFile(file, root);
    }

    public static StateMachine loadStateMachine(File file) throws IOException {
        Object parsed = JsonIO.readFile(file);
        Map<String, Object> root = asMap(parsed, "state-machine");
        Object machineObj = root.get("machine");
        Map<String, Object> machineMap = (machineObj instanceof Map)
                ? asMap(machineObj, "machine")
                : root;
        return basicStateMachineFromMap(machineMap);
    }

    public static void saveMachineLibrary(MachineLibrary library, File file) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("type", "machine-library");
        root.put("library", machineLibraryToMap(library));
        JsonIO.writeFile(file, root);
    }

    public static MachineLibrary loadMachineLibrary(File file) throws IOException {
        Object parsed = JsonIO.readFile(file);
        Map<String, Object> root = asMap(parsed, "machine-library");
        Object libObj = root.get("library");
        Map<String, Object> libMap = (libObj instanceof Map) ? asMap(libObj, "library") : root;
        MachineLibrary library = new MachineLibrary();
        loadLibraryInto(libMap, library);
        return library;
    }

    private static Map<String, Object> pwsControllerToMap(PWSStateMachine machine) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "pws");
        map.put("name", machine.getName());

        List<StateInterface> states = machine.getStates();
        Map<StateInterface, Integer> stateIndex = new LinkedHashMap<>();
        List<Object> stateList = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            StateInterface s = states.get(i);
            stateIndex.put(s, i);
            Map<String, Object> sMap = new LinkedHashMap<>();
            sMap.put("id", i);
            sMap.put("name", s.getName());
            Point p = s.getPosition();
            if (p != null) {
                sMap.put("x", p.x);
                sMap.put("y", p.y);
            }
            if (s instanceof PWSState) {
                PWSState ps = (PWSState) s;
                Map<String, Object> constraints = constraintsToMap(ps);
                if (constraints != null) sMap.put("constraints", constraints);
                String raw = ps.getRawConstraintText();
                if (raw != null) sMap.put("rawConstraintText", raw);
            }
            stateList.add(sMap);
        }
        map.put("states", stateList);

        List<Object> transitionList = new ArrayList<>();
        for (TransitionInterface t : machine.getTransitions()) {
            if (!(t instanceof PWSTransition)) continue;
            PWSTransition pt = (PWSTransition) t;
            Map<String, Object> tMap = new LinkedHashMap<>();
            tMap.put("id", pt.getId());
            tMap.put("source", stateIndex.get(pt.getSource()));
            tMap.put("target", stateIndex.get(pt.getTarget()));
            tMap.put("autonomous", pt.isAutonomous());
            if (pt.getTriggerEvent() != null) tMap.put("triggerEvent", pt.getTriggerEvent());
            Point cp = ((Transition) pt).getControlPoint();
            if (cp != null) tMap.put("controlPoint", pointToMap(cp));
            Point offset = pt.getTriggerOffset();
            if (offset != null) tMap.put("triggerOffset", pointToMap(offset));
            tMap.put("enabled", pt.isEnabled());
            if (pt.getSelfLoopStartAngle() != null) tMap.put("selfLoopStartAngle", pt.getSelfLoopStartAngle());
            if (pt.getSelfLoopEndAngle() != null) tMap.put("selfLoopEndAngle", pt.getSelfLoopEndAngle());
            tMap.put("guard", smPropToMap(pt.getGuardProposition()));
            tMap.put("actions", actionListToList(pt.getActionList()));
            transitionList.add(tMap);
        }
        map.put("transitions", transitionList);

        StateInterface current = machine.getCurrentState();
        if (current != null && stateIndex.containsKey(current)) {
            map.put("currentState", stateIndex.get(current));
        }
        StateInterface pseudo = machine.getPseudoState();
        if (pseudo != null && stateIndex.containsKey(pseudo)) {
            map.put("pseudoState", stateIndex.get(pseudo));
        }
        Set<String> events = machine.getEvents();
        if (events != null && !events.isEmpty()) {
            map.put("events", new ArrayList<>(events));
        }
        return map;
    }

    private static Map<String, Object> basicStateMachineToMap(StateMachine machine) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "base");
        map.put("name", machine.getName());

        List<StateInterface> states = machine.getStates();
        Map<StateInterface, Integer> stateIndex = new LinkedHashMap<>();
        List<Object> stateList = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            StateInterface s = states.get(i);
            stateIndex.put(s, i);
            Map<String, Object> sMap = new LinkedHashMap<>();
            sMap.put("id", i);
            sMap.put("name", s.getName());
            Point p = s.getPosition();
            if (p != null) {
                sMap.put("x", p.x);
                sMap.put("y", p.y);
            }
            stateList.add(sMap);
        }
        map.put("states", stateList);

        List<Object> transitionList = new ArrayList<>();
        for (TransitionInterface t : machine.getTransitions()) {
            if (!(t instanceof Transition)) continue;
            Transition tr = (Transition) t;
            Map<String, Object> tMap = new LinkedHashMap<>();
            tMap.put("id", tr.getId());
            tMap.put("source", stateIndex.get(tr.getSource()));
            tMap.put("target", stateIndex.get(tr.getTarget()));
            tMap.put("autonomous", tr.isAutonomous());
            if (tr.getTriggerEvent() != null) tMap.put("triggerEvent", tr.getTriggerEvent());
            Point cp = tr.getControlPoint();
            if (cp != null) tMap.put("controlPoint", pointToMap(cp));
            Point offset = tr.getTriggerOffset();
            if (offset != null) tMap.put("triggerOffset", pointToMap(offset));
            tMap.put("enabled", tr.isEnabled());
            transitionList.add(tMap);
        }
        map.put("transitions", transitionList);

        StateInterface current = machine.getCurrentState();
        if (current != null && stateIndex.containsKey(current)) {
            map.put("currentState", stateIndex.get(current));
        }
        StateInterface pseudo = machine.getPseudoState();
        if (pseudo != null && stateIndex.containsKey(pseudo)) {
            map.put("pseudoState", stateIndex.get(pseudo));
        }
        Set<String> events = machine.getEvents();
        if (events != null && !events.isEmpty()) {
            map.put("events", new ArrayList<>(events));
        }
        return map;
    }

    private static PWSStateMachine pwsControllerFromMap(Map<String, Object> map, Assembly assembly) throws IOException {
        String name = getString(map, "name", "PWSController");
        PWSStateMachine machine = new PWSStateMachine(name);
        machine.setAssembly(assembly);
        machine.getStates().clear();
        machine.getTransitions().clear();
        machine.getEvents().clear();

        List<Object> states = asList(map.get("states"), "states");
        Map<Integer, PWSState> stateById = new LinkedHashMap<>();
        for (Object o : states) {
            Map<String, Object> sMap = asMap(o, "state");
            int id = getInt(sMap, "id", stateById.size());
            String sName = getString(sMap, "name", "State" + id);
            int x = getInt(sMap, "x", 20);
            int y = getInt(sMap, "y", 20);
            PWSState state = new PWSState(sName, new Point(x, y), assembly);

            Map<String, Object> constraints = asMap(sMap.get("constraints"), null);
            String raw = getString(sMap, "rawConstraintText", null);
            if (constraints != null || raw != null) {
                Semantics sem = semanticsFromConstraints(assembly, constraints, raw);
                state.setConstraintsSemantics(sem);
                String rawText = raw != null ? raw : constraintsRawFallback(constraints);
                if (rawText != null) state.setRawConstraintText(rawText);
            }
            stateById.put(id, state);
            machine.addState(state);
        }

        Integer pseudoId = getNullableInt(map, "pseudoState");
        if (pseudoId != null && stateById.containsKey(pseudoId)) {
            machine.setPseudoState(stateById.get(pseudoId));
        } else {
            for (PWSState st : stateById.values()) {
                if ("PseudoState".equals(st.getName())) {
                    machine.setPseudoState(st);
                    break;
                }
            }
        }

        List<Object> transitions = asList(map.get("transitions"), "transitions");
        for (Object o : transitions) {
            Map<String, Object> tMap = asMap(o, "transition");
            Integer sourceId = getNullableInt(tMap, "source");
            Integer targetId = getNullableInt(tMap, "target");
            if (sourceId == null || targetId == null) continue;
            PWSState source = stateById.get(sourceId);
            PWSState target = stateById.get(targetId);
            if (source == null || target == null) continue;
            boolean autonomous = getBoolean(tMap, "autonomous", false);
            String trigger = getString(tMap, "triggerEvent", "");
            PWSTransition tr = new PWSTransition(source, target, autonomous, trigger, assembly);

            String id = getString(tMap, "id", null);
            if (id != null) tr.setId(id);
            Point cp = pointFromMap(tMap.get("controlPoint"));
            if (cp != null) ((Transition) tr).setControlPoint(cp);
            Point offset = pointFromMap(tMap.get("triggerOffset"));
            if (offset != null) tr.setTriggerOffset(offset);
            tr.setEnabled(getBoolean(tMap, "enabled", true));
            Double startAngle = getNullableDouble(tMap, "selfLoopStartAngle");
            Double endAngle = getNullableDouble(tMap, "selfLoopEndAngle");
            if (startAngle != null) tr.setSelfLoopStartAngle(startAngle);
            if (endAngle != null) tr.setSelfLoopEndAngle(endAngle);

            SMProposition guard = smPropFromMap(tMap.get("guard"), assembly);
            if (guard != null) tr.setGuardProposition(guard);
            ActionList actions = actionListFromList(tMap.get("actions"));
            if (actions != null) tr.setActionList(actions);

            machine.addTransition(tr);
        }

        Integer currentId = getNullableInt(map, "currentState");
        if (currentId != null && stateById.containsKey(currentId)) {
            machine.setCurrentState(stateById.get(currentId));
        }

        List<String> events = asStringList(map.get("events"));
        if (events != null) {
            machine.getEvents().addAll(events);
        }
        return machine;
    }

    private static StateMachine basicStateMachineFromMap(Map<String, Object> map) throws IOException {
        String name = getString(map, "name", "StateMachine");
        StateMachine machine = new StateMachine(name);
        machine.getStates().clear();
        machine.getTransitions().clear();
        machine.getEvents().clear();

        List<Object> states = asList(map.get("states"), "states");
        Map<Integer, State> stateById = new LinkedHashMap<>();
        for (Object o : states) {
            Map<String, Object> sMap = asMap(o, "state");
            int id = getInt(sMap, "id", stateById.size());
            String sName = getString(sMap, "name", "State" + id);
            int x = getInt(sMap, "x", 20);
            int y = getInt(sMap, "y", 20);
            State state = new State(sName, new Point(x, y));
            stateById.put(id, state);
            machine.addState(state);
        }

        Integer pseudoId = getNullableInt(map, "pseudoState");
        if (pseudoId != null && stateById.containsKey(pseudoId)) {
            machine.setPseudoState(stateById.get(pseudoId));
        } else {
            for (State st : stateById.values()) {
                if ("PseudoState".equals(st.getName())) {
                    machine.setPseudoState(st);
                    break;
                }
            }
        }

        List<Object> transitions = asList(map.get("transitions"), "transitions");
        for (Object o : transitions) {
            Map<String, Object> tMap = asMap(o, "transition");
            Integer sourceId = getNullableInt(tMap, "source");
            Integer targetId = getNullableInt(tMap, "target");
            if (sourceId == null || targetId == null) continue;
            State source = stateById.get(sourceId);
            State target = stateById.get(targetId);
            if (source == null || target == null) continue;
            boolean autonomous = getBoolean(tMap, "autonomous", false);
            String trigger = getString(tMap, "triggerEvent", "");
            Transition tr = new Transition(source, target, autonomous, trigger);

            String id = getString(tMap, "id", null);
            if (id != null) tr.setId(id);
            Point cp = pointFromMap(tMap.get("controlPoint"));
            if (cp != null) tr.setControlPoint(cp);
            Point offset = pointFromMap(tMap.get("triggerOffset"));
            if (offset != null) tr.setTriggerOffset(offset);
            tr.setEnabled(getBoolean(tMap, "enabled", true));

            machine.addTransition(tr);
        }

        Integer currentId = getNullableInt(map, "currentState");
        if (currentId != null && stateById.containsKey(currentId)) {
            machine.setCurrentState(stateById.get(currentId));
        }

        List<String> events = asStringList(map.get("events"));
        if (events != null) {
            machine.getEvents().addAll(events);
        }
        return machine;
    }

    private static Map<String, Object> assemblyToMap(Assembly assembly) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", assembly.getAssemblyId());
        Map<String, Object> machineMap = new LinkedHashMap<>();
        MachineLibrary lib = assembly.getMachineLibrary();
        for (Map.Entry<String, StateMachine> entry : assembly.getStateMachines().entrySet()) {
            String id = entry.getKey();
            StateMachine sm = entry.getValue();
            Map<String, Object> entryMap = new LinkedHashMap<>();
            String libKey = findLibraryKey(lib, sm);
            if (libKey != null) {
                entryMap.put("libraryKey", libKey);
            } else {
                entryMap.put("machine", basicStateMachineToMap(sm));
            }
            machineMap.put(id, entryMap);
        }
        map.put("machines", machineMap);
        List<Object> ltlList = new ArrayList<>();
        for (LTLFormula f : assembly.getLTLFormulas()) {
            Map<String, Object> fMap = new LinkedHashMap<>();
            fMap.put("id", f.getId());
            fMap.put("kind", f.getKind());
            fMap.put("text", f.getFormulaText());
            ltlList.add(fMap);
        }
        map.put("ltlFormulas", ltlList);
        return map;
    }

    private static void loadAssemblyInto(Map<String, Object> map, Assembly assembly) throws IOException {
        assembly.getStateMachines().clear();
        Map<String, Object> machines = asMap(map.get("machines"), "machines");
        for (Map.Entry<String, Object> entry : machines.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> entryMap = asMap(entry.getValue(), "machine-entry");
            String libKey = getString(entryMap, "libraryKey", null);
            StateMachine sm = null;
            if (libKey != null) {
                sm = assembly.getMachineLibrary().get(libKey);
            }
            if (sm == null) {
                Object machineObj = entryMap.get("machine");
                if (machineObj instanceof Map) {
                    sm = basicStateMachineFromMap(asMap(machineObj, "machine"));
                } else if (entry.getValue() instanceof Map) {
                    sm = basicStateMachineFromMap(entryMap);
                }
            }
            if (sm != null) {
                assembly.addStateMachine(id, sm);
            }
        }
        List<Object> ltlList = asList(map.get("ltlFormulas"), "ltlFormulas");
        assembly.getLTLFormulas().clear();
        for (Object o : ltlList) {
            Map<String, Object> fMap = asMap(o, "ltlFormula");
            String fid = getString(fMap, "id", "");
            String kind = getString(fMap, "kind", "");
            String text = getString(fMap, "text", "");
            assembly.addLTLFormula(new LTLFormula(fid, text, kind));
        }
    }

    private static Map<String, Object> machineLibraryToMap(MachineLibrary library) {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> machines = new LinkedHashMap<>();
        for (Map.Entry<String, StateMachine> entry : library.getMachines().entrySet()) {
            machines.put(entry.getKey(), basicStateMachineToMap(entry.getValue()));
        }
        map.put("machines", machines);
        return map;
    }

    private static void loadLibraryInto(Map<String, Object> map, MachineLibrary library) throws IOException {
        library.clear();
        Map<String, Object> machines = asMap(map.get("machines"), "machines");
        for (Map.Entry<String, Object> entry : machines.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> mMap = asMap(entry.getValue(), "machine");
            StateMachine sm = basicStateMachineFromMap(mMap);
            library.addMachine(key, sm);
        }
    }

    private static String findLibraryKey(MachineLibrary library, StateMachine machine) {
        for (Map.Entry<String, StateMachine> entry : library.getMachines().entrySet()) {
            if (entry.getValue() == machine) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Map<String, Object> constraintsToMap(PWSState state) {
        Map<String, Object> map = new LinkedHashMap<>();
        String raw = state.getRawConstraintText();
        if (raw != null) map.put("raw", raw);
        boolean any = raw != null && raw.trim().equalsIgnoreCase("ANY");
        map.put("any", any);
        List<Object> lines = constraintLinesFromRaw(raw);
        if (lines != null) map.put("lines", lines);
        return map;
    }

    private static Semantics semanticsFromConstraints(Assembly assembly, Map<String, Object> constraints, String raw) {
        boolean any = (raw != null && raw.trim().equalsIgnoreCase("ANY"));
        if (constraints != null) {
            any = any || getBoolean(constraints, "any", false);
        }
        List<Object> lines = null;
        if (constraints != null) {
            lines = asList(constraints.get("lines"), null);
        }
        if (any || lines == null || lines.isEmpty()) {
            return Semantics.top(assembly);
        }
        Semantics result = Semantics.bottom(assembly);
        for (Object lineObj : lines) {
            List<Object> pairs = asList(lineObj, "constraint line");
            if (pairs.isEmpty()) continue;
            Semantics lineSem = Semantics.top(assembly);
            for (Object pairObj : pairs) {
                Map<String, Object> pair = asMap(pairObj, "constraint pair");
                String machineId = getString(pair, "machineId", null);
                String stateName = getString(pair, "stateName", null);
                if (machineId == null || stateName == null) continue;
                BasicStateProposition bsp = new BasicStateProposition(machineId, stateName);
                lineSem = lineSem.AND(bsp.toSemantics(assembly));
            }
            result = result.OR(lineSem);
        }
        return result;
    }

    private static String constraintsRawFallback(Map<String, Object> constraints) {
        if (constraints == null) return null;
        Boolean any = (Boolean) constraints.get("any");
        if (any != null && any) return "ANY";
        List<Object> lines = asList(constraints.get("lines"), null);
        if (lines == null || lines.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            List<Object> pairs = asList(lines.get(i), null);
            if (pairs == null || pairs.isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n");
            List<String> parts = new ArrayList<>();
            for (Object pairObj : pairs) {
                Map<String, Object> pair = asMap(pairObj, null);
                String machineId = getString(pair, "machineId", null);
                String stateName = getString(pair, "stateName", null);
                if (machineId != null && stateName != null) {
                    parts.add(machineId + "." + stateName);
                }
            }
            sb.append(String.join(", ", parts));
        }
        return sb.toString();
    }

    private static List<Object> constraintLinesFromRaw(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        if ("ANY".equalsIgnoreCase(trimmed)) return null;

        List<Object> lines = new ArrayList<>();
        String[] rawLines = trimmed.split("\\r?\\n");
        for (String line : rawLines) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            if ("ANY".equalsIgnoreCase(l)) continue;
            if (l.startsWith("(") && l.endsWith(")")) {
                l = l.substring(1, l.length() - 1).trim();
            }
            if (l.isEmpty()) continue;
            List<Object> pairs = new ArrayList<>();
            String[] parts = l.split(",");
            for (String part : parts) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                String machineId = null;
                String stateName = null;
                if (p.contains(":")) {
                    String[] split = p.split(":", 2);
                    machineId = split[0].trim();
                    stateName = split[1].trim();
                } else if (p.contains(".")) {
                    String[] split = p.split("\\.", 2);
                    machineId = split[0].trim();
                    stateName = split[1].trim();
                }
                if (machineId != null && stateName != null) {
                    Map<String, Object> pair = new LinkedHashMap<>();
                    pair.put("machineId", machineId);
                    pair.put("stateName", stateName);
                    pairs.add(pair);
                }
            }
            if (!pairs.isEmpty()) lines.add(pairs);
        }
        return lines;
    }

    private static Map<String, Object> annotationDataToMap(PWSStateMachinePanel.AnnotationData data) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (data == null) {
            map.put("states", new ArrayList<>());
            map.put("transitions", new ArrayList<>());
            return map;
        }
        if (data.showExitZoneMachineIds != null) {
            map.put("showExitZoneMachineIds", data.showExitZoneMachineIds);
        }
        if (data.stateDiameter != null) {
            map.put("stateDiameter", data.stateDiameter);
        }
        if (data.stateBorderThickness != null) {
            map.put("stateBorderThickness", data.stateBorderThickness);
        }
        if (data.stateFontSize != null) {
            map.put("stateFontSize", data.stateFontSize);
        }
        List<Object> states = new ArrayList<>();
        for (PWSStateMachinePanel.StateAnnotationData s : data.stateAnnotations) {
            Map<String, Object> sMap = new LinkedHashMap<>();
            sMap.put("stateName", s.stateName);
            if (s.bounds != null) sMap.put("bounds", rectToMap(s.bounds));
            sMap.put("visible", s.visible);
            if (s.offsetX != null && s.offsetY != null) {
                sMap.put("offset", pointToMap(new Point(s.offsetX, s.offsetY)));
            }
            states.add(sMap);
        }
        map.put("states", states);

        List<Object> transitions = new ArrayList<>();
        for (PWSStateMachinePanel.TransitionAnnotationData t : data.transitionAnnotations) {
            Map<String, Object> tMap = new LinkedHashMap<>();
            tMap.put("transitionId", t.transitionId);
            if (t.guardBounds != null) tMap.put("guardBounds", rectToMap(t.guardBounds));
            if (t.actionBounds != null) tMap.put("actionBounds", rectToMap(t.actionBounds));
            if (t.semanticsBounds != null) tMap.put("semanticsBounds", rectToMap(t.semanticsBounds));
            tMap.put("guardVisible", t.guardVisible);
            tMap.put("actionVisible", t.actionVisible);
            tMap.put("semanticsVisible", t.semanticsVisible);
            if (t.guardOffsetX != null && t.guardOffsetY != null) {
                tMap.put("guardOffset", pointToMap(new Point(t.guardOffsetX, t.guardOffsetY)));
            }
            if (t.actionOffsetX != null && t.actionOffsetY != null) {
                tMap.put("actionOffset", pointToMap(new Point(t.actionOffsetX, t.actionOffsetY)));
            }
            if (t.semanticsOffsetX != null && t.semanticsOffsetY != null) {
                tMap.put("semanticsOffset", pointToMap(new Point(t.semanticsOffsetX, t.semanticsOffsetY)));
            }
            transitions.add(tMap);
        }
        map.put("transitions", transitions);
        return map;
    }

    private static PWSStateMachinePanel.AnnotationData annotationDataFromMap(Map<String, Object> map) {
        PWSStateMachinePanel.AnnotationData data = new PWSStateMachinePanel.AnnotationData();
        if (map != null && map.containsKey("showExitZoneMachineIds")) {
            data.showExitZoneMachineIds = getBoolean(map, "showExitZoneMachineIds", true);
        }
        if (map != null) {
            Integer diam = getNullableInt(map, "stateDiameter");
            Double border = getNullableDouble(map, "stateBorderThickness");
            Double font = getNullableDouble(map, "stateFontSize");
            if (diam != null) data.stateDiameter = diam;
            if (border != null) data.stateBorderThickness = border.floatValue();
            if (font != null) data.stateFontSize = font.floatValue();
        }
        List<Object> states = asList(map.get("states"), "states");
        for (Object o : states) {
            Map<String, Object> sMap = asMap(o, "state annotation");
            PWSStateMachinePanel.StateAnnotationData s = new PWSStateMachinePanel.StateAnnotationData();
            s.stateName = getString(sMap, "stateName", null);
            s.bounds = rectFromMap(sMap.get("bounds"));
            s.visible = getBoolean(sMap, "visible", false);
            Point off = pointFromMap(sMap.get("offset"));
            if (off != null) {
                s.offsetX = off.x;
                s.offsetY = off.y;
            }
            data.stateAnnotations.add(s);
        }
        List<Object> transitions = asList(map.get("transitions"), "transitions");
        for (Object o : transitions) {
            Map<String, Object> tMap = asMap(o, "transition annotation");
            PWSStateMachinePanel.TransitionAnnotationData t = new PWSStateMachinePanel.TransitionAnnotationData();
            t.transitionId = getString(tMap, "transitionId", null);
            t.guardBounds = rectFromMap(tMap.get("guardBounds"));
            t.actionBounds = rectFromMap(tMap.get("actionBounds"));
            t.semanticsBounds = rectFromMap(tMap.get("semanticsBounds"));
            t.guardVisible = getBoolean(tMap, "guardVisible", false);
            t.actionVisible = getBoolean(tMap, "actionVisible", false);
            t.semanticsVisible = getBoolean(tMap, "semanticsVisible", false);
            Point guardOff = pointFromMap(tMap.get("guardOffset"));
            if (guardOff != null) {
                t.guardOffsetX = guardOff.x;
                t.guardOffsetY = guardOff.y;
            }
            Point actionOff = pointFromMap(tMap.get("actionOffset"));
            if (actionOff != null) {
                t.actionOffsetX = actionOff.x;
                t.actionOffsetY = actionOff.y;
            }
            Point semOff = pointFromMap(tMap.get("semanticsOffset"));
            if (semOff != null) {
                t.semanticsOffsetX = semOff.x;
                t.semanticsOffsetY = semOff.y;
            }
            data.transitionAnnotations.add(t);
        }
        return data;
    }

    private static Map<String, Object> smPropToMap(SMProposition prop) {
        if (prop == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        if (prop instanceof TrueProposition) {
            map.put("type", "true");
        } else if (prop instanceof FalseProposition) {
            map.put("type", "false");
        } else if (prop instanceof BasicStateProposition) {
            BasicStateProposition bsp = (BasicStateProposition) prop;
            map.put("type", "basic");
            map.put("machineId", bsp.getMachineId());
            map.put("stateName", bsp.getStateName());
        } else if (prop instanceof AndProposition) {
            AndProposition ap = (AndProposition) prop;
            map.put("type", "and");
            map.put("left", smPropToMap(ap.getLeft()));
            map.put("right", smPropToMap(ap.getRight()));
        } else if (prop instanceof OrProposition) {
            OrProposition op = (OrProposition) prop;
            map.put("type", "or");
            map.put("left", smPropToMap(op.getLeft()));
            map.put("right", smPropToMap(op.getRight()));
        } else if (prop instanceof NotProposition) {
            NotProposition np = (NotProposition) prop;
            map.put("type", "not");
            map.put("value", smPropToMap(np.getProposition()));
        } else {
            map.put("type", "text");
            map.put("text", prop.toString());
        }
        return map;
    }

    private static SMProposition smPropFromMap(Object obj, Assembly assembly) {
        if (obj == null) return null;
        if (obj instanceof String) {
            try {
                return SMExpressionParser.parseExpression((String) obj, assembly);
            } catch (Exception ex) {
                return new TrueProposition();
            }
        }
        if (!(obj instanceof Map)) return null;
        Map<String, Object> map = asMap(obj, "guard");
        String type = getString(map, "type", "text");
        switch (type) {
            case "true":
                return new TrueProposition();
            case "false":
                return new FalseProposition();
            case "basic":
                return new BasicStateProposition(getString(map, "machineId", ""), getString(map, "stateName", ""));
            case "and":
                return new AndProposition(smPropFromMap(map.get("left"), assembly), smPropFromMap(map.get("right"), assembly));
            case "or":
                return new OrProposition(smPropFromMap(map.get("left"), assembly), smPropFromMap(map.get("right"), assembly));
            case "not":
                return new NotProposition(smPropFromMap(map.get("value"), assembly));
            default:
                String text = getString(map, "text", null);
                if (text != null) {
                    try {
                        return SMExpressionParser.parseExpression(text, assembly);
                    } catch (Exception ex) {
                        return new TrueProposition();
                    }
                }
                return null;
        }
    }

    private static List<Object> actionListToList(ActionList list) {
        if (list == null) return null;
        List<Object> out = new ArrayList<>();
        for (Action a : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("machineId", a.getMachineId());
            m.put("event", a.getEvent());
            out.add(m);
        }
        return out;
    }

    private static ActionList actionListFromList(Object obj) {
        if (obj == null) return new ActionList();
        List<Object> list = asList(obj, "actions");
        ActionList out = new ActionList();
        for (Object o : list) {
            Map<String, Object> m = asMap(o, "action");
            String machineId = getString(m, "machineId", null);
            String event = getString(m, "event", null);
            if (machineId != null && event != null) {
                out.add(new Action(machineId, event));
            }
        }
        return out;
    }

    private static Map<String, Object> pointToMap(Point p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", p.x);
        map.put("y", p.y);
        return map;
    }

    private static Point pointFromMap(Object obj) {
        if (!(obj instanceof Map)) return null;
        Map<String, Object> map = asMap(obj, "point");
        Integer x = getNullableInt(map, "x");
        Integer y = getNullableInt(map, "y");
        if (x == null || y == null) return null;
        return new Point(x, y);
    }

    private static Map<String, Object> rectToMap(Rectangle r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", r.x);
        map.put("y", r.y);
        map.put("w", r.width);
        map.put("h", r.height);
        return map;
    }

    private static Rectangle rectFromMap(Object obj) {
        if (!(obj instanceof Map)) return null;
        Map<String, Object> map = asMap(obj, "rect");
        Integer x = getNullableInt(map, "x");
        Integer y = getNullableInt(map, "y");
        Integer w = getNullableInt(map, "w");
        Integer h = getNullableInt(map, "h");
        if (x == null || y == null || w == null || h == null) return null;
        return new Rectangle(x, y, w, h);
    }

    private static Map<String, Object> asMap(Object obj, String label) {
        if (obj == null) return new LinkedHashMap<>();
        if (!(obj instanceof Map)) {
            if (label == null) return new LinkedHashMap<>();
            throw new IllegalArgumentException("Expected object for " + label);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) obj;
        return map;
    }

    private static List<Object> asList(Object obj, String label) {
        if (obj == null) return new ArrayList<>();
        if (!(obj instanceof List)) {
            if (label == null) return new ArrayList<>();
            throw new IllegalArgumentException("Expected array for " + label);
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) obj;
        return list;
    }

    private static String getString(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : def;
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return def;
    }

    private static Integer getNullableInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return null;
    }

    private static Double getNullableDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        return null;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    private static List<String> asStringList(Object obj) {
        if (!(obj instanceof List)) return null;
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) obj;
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof String) out.add((String) o);
        }
        return out;
    }
}
