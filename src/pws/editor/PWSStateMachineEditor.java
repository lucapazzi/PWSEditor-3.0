package pws.editor;

import editor.StateMachineEditor;
import pws.PWSStateMachine;

import java.awt.*;

/** PWS-specific editor frame that wires the custom state machine panel. */
@SuppressWarnings("this-escape")
public class PWSStateMachineEditor extends StateMachineEditor {
    private static final long serialVersionUID = 1L;

    public PWSStateMachineEditor(PWSStateMachine stateMachine, String title) {
        super(stateMachine, title);
        // Replace the base panel with the PWS-specific panel.
        getContentPane().remove(statePanel);
        statePanel = new PWSStateMachinePanel(stateMachine);
        getContentPane().add(statePanel, BorderLayout.CENTER);

        revalidate();
        repaint();
    }
}
