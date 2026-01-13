import serializer.BinaryModelSerializer;

public class SerializerSmokeTest {
    public static void main(String[] args) throws Exception {
        String filename = "test_serializer.bin";
        String model = "hello-model";
        String lib = "hello-lib";
        System.out.println("Saving model+library to: " + filename);
        BinaryModelSerializer.saveModelAndLibrary(model, lib, filename);
        System.out.println("Loading back: " + filename);
        Object[] pair = BinaryModelSerializer.loadModelAndLibrary(filename);
        System.out.println("Loaded model: " + pair[0] + " (" + (pair[0] != null ? pair[0].getClass().getName() : "null") + ")");
        System.out.println("Loaded library: " + pair[1] + " (" + (pair[1] != null ? pair[1].getClass().getName() : "null") + ")");
    }
}
