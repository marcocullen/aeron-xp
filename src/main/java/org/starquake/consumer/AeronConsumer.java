package org.starquake.consumer;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.Header;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.DirectBuffer;

public class AeronConsumer {
    //network interface and port to BIND to
    private static final String CHANNEL = "aeron:udp?endpoint=172.16.0.3:40456";
    private static final int STREAM_ID = 10;
    private static final int FRAGMENT_LIMIT = 20;
    private static final IdleStrategy IDLE_STRATEGY = new BackoffIdleStrategy(1, 10, 1000, 1000);

    public static void main(String[] args) {
        MediaDriver.Context driverCtx = new MediaDriver.Context()
                .dirDeleteOnStart(true);

        try (MediaDriver driver = MediaDriver.launch(driverCtx)) {
            System.out.println("Embedded Media Driver started");

            Aeron.Context ctx = new Aeron.Context()
                    .aeronDirectoryName(driverCtx.aeronDirectoryName());

            try (Aeron aeron = Aeron.connect(ctx);
                 Subscription subscription = aeron.addSubscription(CHANNEL, STREAM_ID)) {
                System.out.printf("Subscription ready on channel %s and stream %d%n", CHANNEL, STREAM_ID);

                FragmentAssembler fragmentAssembler = new FragmentAssembler(AeronConsumer::onMessage);

                while (true) {
                    int fragmentsRead = subscription.poll(fragmentAssembler, FRAGMENT_LIMIT);
                    if (fragmentsRead == 0) {
                        IDLE_STRATEGY.idle();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void onMessage(DirectBuffer buffer, int offset, int length, Header header) {
        String message = buffer.getStringWithoutLengthAscii(offset, length);
        System.out.println("Received message: " + message);
    }
}
