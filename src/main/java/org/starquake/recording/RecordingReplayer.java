package org.starquake.recording;

import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.driver.MediaDriver;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

public class RecordingReplayer {
    // Channel we're looking for recordings on - must match producer exactly
    private static final String RECORDING_CHANNEL = "aeron:udp?endpoint=172.16.0.2:40456|control=172.16.0.5:40457";
    private static final String REPLAY_CHANNEL = "aeron:udp?endpoint=172.16.0.6:40456";
    private static final int STREAM_ID = 10;
    private static final IdleStrategy IDLE_STRATEGY = new BackoffIdleStrategy();


    // Record class to store recording details
    private static class Recording {
        final long recordingId;
        final long startPosition;
        final long stopPosition;
        final int streamId;
        final String channel;

        Recording(long recordingId, long startPosition, long stopPosition,
                  int streamId, String channel) {
            this.recordingId = recordingId;
            this.startPosition = startPosition;
            this.stopPosition = stopPosition;
            this.streamId = streamId;
            this.channel = channel;
        }
    }

    private static Recording findLatestRecording(AeronArchive archive) {
        System.out.println("Searching for recordings..." + RECORDING_CHANNEL);

        // Storage for all found recordings
        final Recording[] latestRecording = new Recording[1];

        // First list all recordings to see what's available
        archive.listRecordingsForUri(
                0,    // from record id
                Integer.MAX_VALUE,  // max number of records
                RECORDING_CHANNEL,  // specific channel we want
                STREAM_ID,         // specific stream we want
                (controlSessionId, correlationId, recordingId, startTimestamp,
                 stopTimestamp, startPosition, stopPosition, initialTermId,
                 segmentFileLength, termBufferLength, mtuLength, sessionId,
                 streamId, strippedChannel, originalChannel, sourceIdentity) -> {
                    System.out.printf("Found matching recording: ID=%d, channel=%s, stream=%d%n",
                            recordingId, strippedChannel, streamId);

                    // Keep track of the latest recording (highest ID)
                    if (latestRecording[0] == null || recordingId > latestRecording[0].recordingId) {
                        latestRecording[0] = new Recording(recordingId, startPosition,
                                stopPosition, streamId, strippedChannel);
                    }
                });

        return latestRecording[0];
    }

    private static void replayRecording(AeronArchive archive, Recording recording) {
        System.out.printf("Replaying recording: ID=%d from position %d to %d%n",
                recording.recordingId, recording.startPosition, recording.stopPosition);

        int replayStreamId = STREAM_ID + 1;
        long length = recording.stopPosition - recording.startPosition;

        // Start replay session
        long replaySessionId = archive.startReplay(
                recording.recordingId,
                recording.startPosition,
                length,
                REPLAY_CHANNEL,
                replayStreamId);

        System.out.println("Started replay session: " + replaySessionId);

        // Create subscription for replay
        try (Subscription subscription = archive.context().aeron()
                .addSubscription(REPLAY_CHANNEL, replayStreamId)) {

            System.out.println("Waiting for replay...");
            FragmentAssembler fragmentAssembler = new FragmentAssembler(
                    (buffer, offset, bufferLength, header) -> {
                        final String message = buffer.getStringWithoutLengthAscii(offset, bufferLength);
                        System.out.println("Replayed message: " + message);
                    });

            // Poll with timeout
            long startTime = System.currentTimeMillis();
            long timeout = 5000; // 5 seconds

            while (System.currentTimeMillis() - startTime < timeout) {
                final int fragmentsRead = subscription.poll(fragmentAssembler, 10);
                if (fragmentsRead > 0) {
                    System.out.println("Read " + fragmentsRead + " fragments");
                }
                IDLE_STRATEGY.idle();
            }
        }
    }

    public static void main(String[] args) {
        MediaDriver.Context driverCtx = new MediaDriver.Context()
                .dirDeleteOnStart(true);

        try (MediaDriver driver = MediaDriver.launch(driverCtx)) {
            System.out.println("Recording Replayer Media Driver started");

            AeronArchive.Context archiveCtx = new AeronArchive.Context()
                    .controlRequestChannel("aeron:udp?endpoint=172.16.0.2:8010")
                    .controlResponseChannel("aeron:udp?endpoint=172.16.0.6:8020");

            try (AeronArchive archive = AeronArchive.connect(archiveCtx)) {
                System.out.println("Connected to Archive");

                Recording latestRecording = findLatestRecording(archive);
                if (latestRecording != null) {
                    replayRecording(archive, latestRecording);
                    while (true) {
                        Thread.sleep(5000);
                        System.out.println("Replayer healthy...");
                    }
                } else {
                    System.out.println("No recordings found");
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}