package io.lettuce.core.protocol;

import static io.lettuce.TestTags.UNIT_TEST;
import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandInterruptedException;
import io.lettuce.core.RedisException;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.internal.Futures;
import io.lettuce.core.output.CommandOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.test.TestFutures;

/**
 * Unit tests for {@link AsyncCommand}.
 *
 * @author Mark Paluch
 */
@Tag(UNIT_TEST)
public class AsyncCommandUnitTests {

    private RedisCodec<String, String> codec = StringCodec.UTF8;

    private Command<String, String, String> internal;

    private AsyncCommand<String, String, String> sut;

    @BeforeEach
    final void createCommand() {
        CommandOutput<String, String, String> output = new StatusOutput<>(codec);
        internal = new Command<>(CommandType.INFO, output, null);
        sut = new AsyncCommand<>(internal);
    }

    @Test
    void isCancelled() {
        assertThat(sut.isCancelled()).isFalse();
        assertThat(sut.cancel(true)).isTrue();
        assertThat(sut.isCancelled()).isTrue();
        assertThat(sut.cancel(true)).isTrue();
    }

    @Test
    void isDone() {
        assertThat(sut.isDone()).isFalse();
        sut.complete();
        assertThat(sut.isDone()).isTrue();
    }

    @Test
    void awaitAllCompleted() {
        sut.complete();
        assertThat(Futures.awaitAll(-1, TimeUnit.MILLISECONDS, sut)).isTrue();
        assertThat(Futures.awaitAll(0, TimeUnit.MILLISECONDS, sut)).isTrue();
        assertThat(Futures.await(5, TimeUnit.MILLISECONDS, sut)).isTrue();
    }

    @Test
    void awaitAll() {
        assertThat(Futures.awaitAll(1, TimeUnit.NANOSECONDS, sut)).isFalse();
    }

    @Test
    void awaitReturnsCompleted() {
        sut.getOutput().set(StandardCharsets.US_ASCII.encode("one"));
        sut.complete();
        assertThat(Futures.awaitOrCancel(sut, -1, TimeUnit.NANOSECONDS)).isEqualTo("one");
        assertThat(Futures.awaitOrCancel(sut, 0, TimeUnit.NANOSECONDS)).isEqualTo("one");
        assertThat(Futures.awaitOrCancel(sut, 1, TimeUnit.NANOSECONDS)).isEqualTo("one");
    }

    @Test
    void awaitWithExecutionException() {
        sut.completeExceptionally(new RedisException("error"));
        assertThatThrownBy(() -> Futures.awaitOrCancel(sut, 1, TimeUnit.SECONDS)).isInstanceOf(RedisException.class);
    }

    @Test
    void awaitWithCancelledCommand() {
        sut.cancel();
        assertThatThrownBy(() -> Futures.awaitOrCancel(sut, 5, TimeUnit.SECONDS)).isInstanceOf(CancellationException.class);
    }

    @Test
    void awaitAllWithExecutionException() {
        sut.completeExceptionally(new RedisCommandExecutionException("error"));

        assertThatThrownBy(() -> Futures.await(0, TimeUnit.SECONDS, sut)).isInstanceOf(RedisException.class);
    }

    @Test
    void getError() {
        sut.getOutput().setError("error");
        assertThat(internal.getError()).isEqualTo("error");
    }

    @Test
    void getErrorAsync() {
        sut.getOutput().setError("error");
        sut.complete();
        assertThat(sut).isCompletedExceptionally();
    }

    @Test
    void completeExceptionally() {
        sut.completeExceptionally(new RuntimeException("test"));
        assertThat(internal.getError()).isEqualTo("test");

        assertThat(sut).isCompletedExceptionally();
    }

    @Test
    void asyncGet() {
        sut.getOutput().set(StandardCharsets.US_ASCII.encode("one"));
        sut.complete();
        assertThat(TestFutures.getOrTimeout(sut.toCompletableFuture())).isEqualTo("one");
        sut.getOutput().toString();
    }

    @Test
    void customKeyword() {
        sut = new AsyncCommand<>(new Command<>(MyKeywords.DUMMY, new StatusOutput<>(codec), null));

        assertThat(sut.toString()).contains(MyKeywords.DUMMY.name());
    }

    @Test
    void customKeywordWithArgs() {
        sut = new AsyncCommand<>(new Command<>(MyKeywords.DUMMY, null, new CommandArgs<>(codec)));
        sut.getArgs().add(MyKeywords.DUMMY);
        assertThat(sut.getArgs().toString()).contains(MyKeywords.DUMMY.name());
    }

    @Test
    void getWithTimeout() throws Exception {
        sut.getOutput().set(StandardCharsets.US_ASCII.encode("one"));
        sut.complete();

        assertThat(sut.get(0, TimeUnit.MILLISECONDS)).isEqualTo("one");
    }

    @Test
    void getTimeout() {
        assertThatThrownBy(() -> sut.get(2, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
    }

    @Test
    void awaitTimeout() {
        assertThat(sut.await(2, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    void getInterrupted() {
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> sut.get()).isInstanceOf(InterruptedException.class);
    }

    @Test
    void getInterrupted2() {
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> sut.get(5, TimeUnit.MILLISECONDS)).isInstanceOf(InterruptedException.class);
    }

    @Test
    void awaitInterrupted2() {
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> sut.await(5, TimeUnit.MILLISECONDS)).isInstanceOf(RedisCommandInterruptedException.class);
    }

    @Test
    void outputSubclassOverride1() {
        CommandOutput<String, String, String> output = new CommandOutput<String, String, String>(codec, null) {

            @Override
            public String get() throws RedisException {
                return null;
            }

        };
        assertThatThrownBy(() -> output.set(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void outputSubclassOverride2() {
        CommandOutput<String, String, String> output = new CommandOutput<String, String, String>(codec, null) {

            @Override
            public String get() throws RedisException {
                return null;
            }

        };
        assertThatThrownBy(() -> output.set(0)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sillyTestsForEmmaCoverage() {
        assertThat(CommandType.valueOf("APPEND")).isEqualTo(CommandType.APPEND);
        assertThat(CommandKeyword.valueOf("AFTER")).isEqualTo(CommandKeyword.AFTER);
    }

    private enum MyKeywords implements ProtocolKeyword {

        DUMMY;

        @Override
        public byte[] getBytes() {
            return name().getBytes();
        }

    }

}
