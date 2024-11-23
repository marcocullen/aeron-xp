package org.starquake.producer;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.driver.MediaDriver;
import org.agrona.concurrent.UnsafeBuffer;

public class AeronProducer {
    //network interface and port to CONNECT to
    private static final String CHANNEL = "aeron:udp?endpoint=172.16.0.3:40456";
    private static final int STREAM_ID = 10;
    private static final int MESSAGE_LENGTH = 32;

    public static void main(String[] args) {
        MediaDriver.Context driverCtx = new MediaDriver.Context()
                .dirDeleteOnStart(true);

        try (MediaDriver driver = MediaDriver.launch(driverCtx)) {
            System.out.println("Producer Media Driver started");

            Aeron.Context ctx = new Aeron.Context()
                    .aeronDirectoryName(driverCtx.aeronDirectoryName());

            try (Aeron aeron = Aeron.connect(ctx);
                 Publication publication = aeron.addPublication(CHANNEL, STREAM_ID)) {

                UnsafeBuffer buffer = new UnsafeBuffer(new byte[MESSAGE_LENGTH]);
                String message = "Hello, batman!";
                buffer.putStringWithoutLengthAscii(0, message);

                while (publication.offer(buffer, 0, MESSAGE_LENGTH) < 0L) {
                    System.out.println("Offer failed");
                    Thread.sleep(10);
                }

                System.out.println("Message sent: " + message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
