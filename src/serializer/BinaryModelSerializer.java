package serializer;

import java.io.*;

/** Binary serializer for saving and loading editor models. */
public class BinaryModelSerializer {
    /** Utility class; do not instantiate. */
    private BinaryModelSerializer() {
    }

    /**
     * Saves a single serializable model to disk.
     *
     * @param model model to save
     * @param filename output file path
     * @throws IOException if the file cannot be written
     */
    public static void saveModel(Serializable model, String filename) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filename);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(model);
        }
    }

    /**
     * Save a model followed by an auxiliary object (e.g. MachineLibrary) into the same file.
     * This writes two consecutive objects which can be read back with {@link #loadModelAndLibrary}.
     *
     * @param model model to save
     * @param library auxiliary object to save
     * @param filename output file path
     * @throws IOException if the file cannot be written
     */
    public static void saveModelAndLibrary(Serializable model, Serializable library, String filename) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filename);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(model);
            oos.writeObject(library);
        }
    }

    /**
     * Load a file previously written with {@link #saveModelAndLibrary} and return an array
     * where index 0 is the model and index 1 is the library (may be null if absent).
     *
     * @param filename input file path
     * @return array containing model and optional library
     * @throws IOException if the file cannot be read
     * @throws ClassNotFoundException if a class in the stream is missing
     */
    public static Object[] loadModelAndLibrary(String filename) throws IOException, ClassNotFoundException {
        try (FileInputStream fis = new FileInputStream(filename);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            Object model = ois.readObject();
            Object library = null;
            try {
                library = ois.readObject();
            } catch (EOFException eof) {
                // library absent — older file format
            } catch (Exception ex) {
                // Could not deserialize library (e.g. serialVersionUID mismatch).
                // Return the exception as second element so callers can handle gracefully.
                return new Object[]{model, ex};
            }
            return new Object[]{model, library};
        }
    }

     /**
      * Loads a single model saved with {@link #saveModel}.
      *
      * @param filename input file path
      * @return deserialized model object
      * @throws IOException if the file cannot be read
      * @throws ClassNotFoundException if a class in the stream is missing
      */
    public static Object loadModel(String filename) throws IOException, ClassNotFoundException {
        try (FileInputStream fis = new FileInputStream(filename);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            return ois.readObject();
        }
    }
}
