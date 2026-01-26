package test;

import machinery.StateMachine;
import serializer.JsonModelSerializer;
import java.io.File;

public class SerializerSmokeTest {
    public static void main(String[] args) throws Exception {
        String filename = "test_state_machine.sm";
        StateMachine model = new StateMachine("hello-model");
        System.out.println("Saving state machine to: " + filename);
        JsonModelSerializer.saveStateMachine(model, new File(filename));
        System.out.println("Loading back: " + filename);
        StateMachine loaded = JsonModelSerializer.loadStateMachine(new File(filename));
        System.out.println("Loaded model: " + loaded.getName() + " (" + loaded.getClass().getName() + ")");
    }
}
