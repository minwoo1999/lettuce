package io.lettuce.core.dynamic.output;

import static io.lettuce.TestTags.UNIT_TEST;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.lettuce.core.ScoredValue;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.dynamic.support.ClassTypeInformation;
import io.lettuce.core.dynamic.support.ResolvableType;
import io.lettuce.core.output.*;

/**
 * @author Mark Paluch
 */
@Tag(UNIT_TEST)
class OutputRegistryUnitTests {

    @Test
    void getKeyOutputType() {

        OutputType outputComponentType = OutputRegistry.getOutputComponentType(KeyListOutput.class);

        assertThat(outputComponentType.getTypeInformation().getComponentType().getType()).isEqualTo(Object.class);
    }

    @Test
    void getStringListOutputType() {

        OutputType outputComponentType = OutputRegistry.getOutputComponentType(StringListOutput.class);

        assertThat(outputComponentType.getTypeInformation().getComponentType())
                .isEqualTo(ClassTypeInformation.from(String.class));
    }

    @Test
    void componentTypeOfKeyOuputWithCodecIsAssignableFromString() {

        OutputType outputComponentType = OutputRegistry.getOutputComponentType(KeyOutput.class);

        ResolvableType resolvableType = outputComponentType.withCodec(new StringCodec());

        assertThat(resolvableType.isAssignableFrom(String.class)).isTrue();
    }

    @Test
    void componentTypeOfKeyListOuputWithCodecIsAssignableFromListOfString() {

        OutputType outputComponentType = OutputRegistry.getOutputComponentType(KeyListOutput.class);

        ResolvableType resolvableType = outputComponentType.withCodec(new StringCodec());

        assertThat(resolvableType.isAssignableFrom(ResolvableType.forClassWithGenerics(List.class, String.class))).isTrue();
    }

    @Test
    void streamingTypeOfKeyOuputWithCodecIsAssignableFromString() {

        OutputType outputComponentType = OutputRegistry.getStreamingType(KeyListOutput.class);

        ResolvableType resolvableType = outputComponentType.withCodec(new StringCodec());

        assertThat(resolvableType.isAssignableFrom(ResolvableType.forClass(String.class))).isTrue();
    }

    @Test
    void streamingTypeOfKeyListOuputWithCodecIsAssignableFromListOfString() {

        OutputType outputComponentType = OutputRegistry.getStreamingType(ScoredValueListOutput.class);

        ResolvableType resolvableType = outputComponentType.withCodec(new StringCodec());

        assertThat(resolvableType.isAssignableFrom(ResolvableType.forClassWithGenerics(ScoredValue.class, String.class)))
                .isTrue();
    }

    @Test
    void customizedValueOutput() {

        OutputType outputComponentType = OutputRegistry.getOutputComponentType(KeyTypedOutput.class);

        ResolvableType resolvableType = outputComponentType.withCodec(ByteArrayCodec.INSTANCE);

        assertThat(resolvableType.isAssignableFrom(ResolvableType.forClass(byte[].class))).isTrue();
    }

    private static abstract class IntermediateOutput<K1, V1> extends CommandOutput<K1, V1, V1> {

        IntermediateOutput(RedisCodec<K1, V1> codec, V1 output) {
            super(codec, null);
        }

    }

    private static class KeyTypedOutput extends IntermediateOutput<ByteBuffer, byte[]> {

        KeyTypedOutput(RedisCodec<ByteBuffer, byte[]> codec) {
            super(codec, null);
        }

    }

}
