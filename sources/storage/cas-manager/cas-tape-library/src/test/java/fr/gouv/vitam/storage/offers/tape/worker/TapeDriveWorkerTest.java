package fr.gouv.vitam.storage.offers.tape.worker;

import fr.gouv.vitam.common.logging.SysErrLogger;
import fr.gouv.vitam.common.storage.tapelibrary.ReadWritePriority;
import fr.gouv.vitam.common.storage.tapelibrary.TapeDriveConf;
import fr.gouv.vitam.storage.offers.tape.cas.ArchiveOutputRetentionPolicy;
import fr.gouv.vitam.storage.offers.tape.cas.ReadRequestReferentialRepository;
import fr.gouv.vitam.storage.offers.tape.cas.ArchiveReferentialRepository;
import fr.gouv.vitam.storage.offers.tape.exception.QueueException;
import fr.gouv.vitam.storage.offers.tape.spec.TapeCatalogService;
import fr.gouv.vitam.storage.offers.tape.spec.TapeDriveService;
import fr.gouv.vitam.storage.offers.tape.spec.TapeRobotPool;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.internal.verification.VerificationModeFactory;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class TapeDriveWorkerTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();


    @Mock
    private TapeRobotPool tapeRobotPool;

    @Mock
    private TapeDriveService tapeDriveService;


    @Mock
    private ArchiveReferentialRepository archiveReferentialRepository;

    @Mock
    private ReadRequestReferentialRepository readRequestReferentialRepository;

    @Mock
    private TapeCatalogService tapeCatalogService;

    @Mock
    private ArchiveOutputRetentionPolicy archiveOutputRetentionPolicy;

    @Spy
    private TapeDriveOrderConsumer tapeDriveOrderConsumer;

    @Mock
    private TapeDriveConf tapeDriveConf;

    @Before
    public void setUp() throws Exception {
        reset(tapeRobotPool);
        reset(tapeDriveService);
        reset(tapeCatalogService);
        reset(tapeDriveOrderConsumer);
        reset(tapeDriveConf);
        reset(archiveReferentialRepository);
        reset(archiveOutputRetentionPolicy);
        when(tapeDriveService.getTapeDriveConf()).thenReturn(tapeDriveConf);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void test_constructor() {
        new TapeDriveWorker(tapeRobotPool, tapeDriveService, tapeCatalogService, tapeDriveOrderConsumer,
            archiveReferentialRepository, readRequestReferentialRepository, null,
            "/tmp", false, archiveOutputRetentionPolicy);

        try {
            new TapeDriveWorker(null, tapeDriveService, tapeCatalogService, tapeDriveOrderConsumer,
                archiveReferentialRepository, readRequestReferentialRepository, null, "/tmp", false,
                archiveOutputRetentionPolicy);
            Assertions.fail("Should fail tapeRobotPool required");
        } catch (Exception e) {
            SysErrLogger.FAKE_LOGGER.ignoreLog(e);
        }

        try {
            new TapeDriveWorker(tapeRobotPool, null, tapeCatalogService, tapeDriveOrderConsumer,
                archiveReferentialRepository, readRequestReferentialRepository, null, "/tmp", false,
                archiveOutputRetentionPolicy);
            Assertions.fail("Should fail tapeDriveService required");
        } catch (Exception e) {
            SysErrLogger.FAKE_LOGGER.ignoreLog(e);
        }

        try {
            new TapeDriveWorker(tapeRobotPool, tapeDriveService, null, tapeDriveOrderConsumer, archiveReferentialRepository, readRequestReferentialRepository,
                null, "/tmp", false, archiveOutputRetentionPolicy);
            Assertions.fail("Should fail tapeCatalogService required");
        } catch (Exception e) {
            SysErrLogger.FAKE_LOGGER.ignoreLog(e);
        }

        try {
            new TapeDriveWorker(tapeRobotPool, tapeDriveService, tapeCatalogService, null, archiveReferentialRepository, readRequestReferentialRepository,
                null, "/tmp", false, archiveOutputRetentionPolicy);
            Assertions.fail("Should fail tapeDriveOrderConsumer required");
        } catch (Exception e) {
            SysErrLogger.FAKE_LOGGER.ignoreLog(e);
        }

        try {
            new TapeDriveWorker(tapeRobotPool, tapeDriveService, tapeCatalogService, tapeDriveOrderConsumer, null, readRequestReferentialRepository, null,
                "/tmp", false, archiveOutputRetentionPolicy);
            Assertions.fail("Should fail archiveReferentialRepository required");
        } catch (Exception e) {
            SysErrLogger.FAKE_LOGGER.ignoreLog(e);
        }

        try {
            new TapeDriveWorker(tapeRobotPool, tapeDriveService, tapeCatalogService, tapeDriveOrderConsumer, archiveReferentialRepository, null, null,
                    "/tmp", false, archiveOutputRetentionPolicy);
            Assertions.fail("Should fail readRequestReferentialRepository required");
        } catch (Exception e) {
            SysErrLogger.FAKE_LOGGER.ignoreLog(e);
        }

        try {
            new TapeDriveWorker(tapeRobotPool, tapeDriveService, tapeCatalogService, tapeDriveOrderConsumer, archiveReferentialRepository, readRequestReferentialRepository, null,
                "/tmp", false, null);
            Assertions.fail("Should fail archiveOutputRetentionPolicy required");
        } catch (Exception e) {
            SysErrLogger.FAKE_LOGGER.ignoreLog(e);
        }

    }

    @Test
    public void run() {
        // TODO (djh): 28/03/19
    }


    @Test
    public void stop_wait() throws InterruptedException {
        TapeDriveWorker tapeDriveWorker =
            new TapeDriveWorker(tapeRobotPool, tapeDriveService, tapeCatalogService, tapeDriveOrderConsumer,
                archiveReferentialRepository, readRequestReferentialRepository, null, null, 1000, false,
                archiveOutputRetentionPolicy);
        Thread thread1 = new Thread(tapeDriveWorker);
        thread1.start();
        tapeDriveWorker.stop();
        Thread.sleep(2);
        Assertions.assertThat(tapeDriveWorker.isRunning()).isFalse();

    }

    @Test
    public void stop_no_wait() throws QueueException, InterruptedException {
        TapeDriveWorker tapeDriveWorker =
            new TapeDriveWorker(tapeRobotPool, tapeDriveService, tapeCatalogService, tapeDriveOrderConsumer,
                archiveReferentialRepository, readRequestReferentialRepository, null, null, 100, false,
                archiveOutputRetentionPolicy);

        when(tapeDriveOrderConsumer.consume(any())).thenAnswer(o -> {
            Thread.sleep(20);
            return Optional.empty();
        });
        Thread thread1 = new Thread(tapeDriveWorker);
        thread1.start();
        Thread.sleep(5);

        tapeDriveWorker.stop(1, TimeUnit.MICROSECONDS);
        Assertions.assertThat(tapeDriveWorker.isRunning()).isTrue();

        Thread.sleep(150);

        Assertions.assertThat(tapeDriveWorker.isRunning()).isFalse();
    }

    @Test
    public void test_get_index_ok() throws QueueException {
        TapeDriveWorker tapeDriveWorker =
            new TapeDriveWorker(tapeRobotPool, tapeDriveService, tapeCatalogService, tapeDriveOrderConsumer,
                archiveReferentialRepository, readRequestReferentialRepository, null, null, 1000, false,
                archiveOutputRetentionPolicy);
        when(tapeDriveConf.getIndex()).thenReturn(1);
        when(tapeDriveOrderConsumer.consume(eq(tapeDriveWorker))).thenAnswer(o -> {
            Thread.sleep(5);
            return Optional.empty();
        });
        Thread thread1 = new Thread(tapeDriveWorker);
        thread1.start();
        Assertions.assertThat(tapeDriveWorker.getIndex()).isEqualTo(1);
        verify(tapeDriveConf, VerificationModeFactory.times(3)).getIndex();


        tapeDriveWorker.stop();
        Assertions.assertThat(tapeDriveWorker.isRunning()).isFalse();
    }

    @Test
    public void test_get_priority_ok() throws QueueException {
        TapeDriveWorker tapeDriveWorker =
            new TapeDriveWorker(tapeRobotPool, tapeDriveService, tapeCatalogService, tapeDriveOrderConsumer,
                archiveReferentialRepository, readRequestReferentialRepository, null, null, 1000, false,
                archiveOutputRetentionPolicy);
        when(tapeDriveConf.getReadWritePriority()).thenReturn(ReadWritePriority.READ);
        when(tapeDriveOrderConsumer.consume(any())).thenAnswer(o -> {
            Thread.sleep(5);
            return Optional.empty();
        });
        Thread thread1 = new Thread(tapeDriveWorker);
        thread1.start();
        Assertions.assertThat(tapeDriveWorker.getPriority()).isEqualTo(ReadWritePriority.READ);
        verify(tapeDriveConf, VerificationModeFactory.times(1)).getReadWritePriority();


        tapeDriveWorker.stop();
        Assertions.assertThat(tapeDriveWorker.isRunning()).isFalse();
    }

    @Test
    public void test_get_read_write_result_and_current_tape() throws QueueException {
        TapeDriveWorker tapeDriveWorker =
            new TapeDriveWorker(tapeRobotPool, tapeDriveService, tapeCatalogService, tapeDriveOrderConsumer,
                archiveReferentialRepository, readRequestReferentialRepository, null, null, 1000, false,
                archiveOutputRetentionPolicy);

        when(tapeDriveConf.getReadWritePriority()).thenReturn(ReadWritePriority.READ);
        when(tapeDriveOrderConsumer.consume(any())).thenAnswer(o -> {
            Thread.sleep(5);
            return Optional.empty();
        });
        Thread thread1 = new Thread(tapeDriveWorker);
        thread1.start();
        Assertions.assertThat(tapeDriveWorker.getReadWriteResult()).isNull();
        Assertions.assertThat(tapeDriveWorker.getCurrentTape()).isNull();


        tapeDriveWorker.stop();
        Assertions.assertThat(tapeDriveWorker.isRunning()).isFalse();
    }
}
