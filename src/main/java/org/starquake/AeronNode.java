package org.starquake;
import io.aeron.driver.MediaDriver;

public class AeronNode {
    public static void main(String[] args) {
        try (MediaDriver mediaDriver = MediaDriver.launch()) {
            System.out.println("Aeron Media Driver started");
            // Keep the driver running
            while (true) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
