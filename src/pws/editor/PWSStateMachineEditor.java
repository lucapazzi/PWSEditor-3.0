package pws.editor;

import editor.StateMachineEditor;
import pws.PWSStateMachine;

import java.awt.*;

/** PWS-specific editor frame that wires the custom state machine panel. */
public class PWSStateMachineEditor extends StateMachineEditor {

    public PWSStateMachineEditor(PWSStateMachine stateMachine, String title) {
        super(stateMachine, title);
        // Sostituisce il pannello base con il pannello specifico per PWS.
        getContentPane().remove(statePanel);
        statePanel = new PWSStateMachinePanel(stateMachine);
        getContentPane().add(statePanel, BorderLayout.CENTER);

        revalidate();
        repaint();
    }
}
