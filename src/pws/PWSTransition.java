package pws;

import assembly.Action;
import assembly.ActionList;
import assembly.Assembly;
import machinery.StateInterface;
import machinery.Transition;
import pws.editor.annotation.ActionAnnotation;
import pws.editor.annotation.GuardAnnotation;
import pws.editor.annotation.TransitionSemanticsAnnotation;
import pws.editor.semantics.Semantics;
import smalgebra.SMProposition;
import smalgebra.TrueProposition;

import java.io.Serializable;

/** PWS transition with guard, actions, and semantics annotations. */
public class PWSTransition extends Transition implements Serializable {
    public static final String INIT_TRIGGER_EVENT = "_init";
    // Nuovi campi semantici
    private Assembly assembly;
    private SMProposition guardProposition;
    private ActionList actionList;  // o List<Action>
    private Semantics transitionSemantics;

    // Componenti per annotazioni interattive (utilizzeremo le nuove sottoclassi)
    private transient GuardAnnotation guardAnnotation;
    private transient ActionAnnotation actionAnnotation;
    private transient TransitionSemanticsAnnotation semanticsAnnotation;
    /** Whether this transition is enabled (drawn black and contributes semantics). */
    private boolean enabled = true;
    
    // Self-loop angles (in degrees) - null means use default (135 and 45)
    private Double selfLoopStartAngle;
    private Double selfLoopEndAngle;

    /** Returns whether this transition is enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables this transition.
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    public PWSTransition(Assembly assembly) {
        super();
        this.assembly = assembly;
        guardProposition = new TrueProposition();
        actionList = new ActionList();
        transitionSemantics = Semantics.bottom(assembly.getAssemblyId());
    }

    public PWSTransition(StateInterface source, StateInterface target, boolean autonomous, String triggerEvent, Assembly assembly) {
        super(source, target, autonomous, triggerEvent);
        this.assembly = assembly;
        guardProposition = new TrueProposition();
        actionList = new ActionList();
        transitionSemantics = Semantics.bottom(assembly.getAssemblyId());
    }

    public PWSTransition(StateInterface source, StateInterface target, boolean autonomous, Assembly assembly) {
        super(source, target, autonomous);
        this.assembly = assembly;
        guardProposition = new TrueProposition();
        actionList = new ActionList();
        transitionSemantics = Semantics.bottom(assembly.getAssemblyId());;
    }

    public SMProposition getGuardProposition() {
        return guardProposition;
    }

    public void setGuardProposition(SMProposition guardProposition) {
        this.guardProposition = guardProposition;
    }

    public ActionList getActionList() {
        return actionList;
    }

    public void addAction(Action action) {
        actionList.add(action);
    }

    public void setActionList(ActionList actionList) {
        this.actionList = actionList;
    }

    public Semantics getTransitionSemantics() {
        return transitionSemantics;
    }

    public boolean isInitialTransition() {
        StateInterface src = getSource();
        if (!(src instanceof PWSState ps) || !ps.isPseudoState()) {
            return false;
        }
        String trigger = getTriggerEvent();
        if (!isAutonomous() && INIT_TRIGGER_EVENT.equals(trigger)) {
            return true;
        }
        return isAutonomous() && (trigger == null || trigger.isBlank());
    }

    public void setTransitionSemantics(Semantics transitionSemantics) {
        this.transitionSemantics = transitionSemantics;
        if (semanticsAnnotation != null) {
            semanticsAnnotation.setContent(transitionSemantics);
            semanticsAnnotation.repaint();
        }
    }

    public GuardAnnotation getGuardAnnotation() {
        return guardAnnotation;
    }

    public void setGuardAnnotation(GuardAnnotation guardAnnotation) {
        this.guardAnnotation = guardAnnotation;
    }

    public ActionAnnotation getActionAnnotation() {
        return actionAnnotation;
    }

    public void setActionAnnotation(ActionAnnotation actionAnnotation) {
        this.actionAnnotation = actionAnnotation;
    }

    public TransitionSemanticsAnnotation getSemanticsAnnotation() {
        return semanticsAnnotation;
    }

    public void setSemanticsAnnotation(TransitionSemanticsAnnotation semanticsAnnotation) {
        this.semanticsAnnotation = semanticsAnnotation;
    }
    
    public Double getSelfLoopStartAngle() {
        return selfLoopStartAngle;
    }
    
    public void setSelfLoopStartAngle(Double angle) {
        this.selfLoopStartAngle = angle;
    }
    
    public Double getSelfLoopEndAngle() {
        return selfLoopEndAngle;
    }
    
    public void setSelfLoopEndAngle(Double angle) {
        this.selfLoopEndAngle = angle;
    }
}
