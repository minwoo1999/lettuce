/*
 * Copyright 2011-Present, Redis Ltd. and Contributors
 * All rights reserved.
 *
 * Licensed under the MIT License.
 *
 * This file contains contributions from third-party contributors
 * licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.lettuce.apigenerator;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.PrettyPrinterConfiguration;

import io.lettuce.core.internal.LettuceSets;

import static io.lettuce.TestTags.API_GENERATOR;

/**
 * Create reactive API based on the templates.
 *
 * @author Mark Paluch
 */
public class CreateReactiveApi {

    public static Set<String> KEEP_METHOD_RESULT_TYPE = LettuceSets.unmodifiableSet("digest", "close", "isOpen",
            "BaseRedisCommands.reset", "getStatefulConnection", "setAutoFlushCommands", "flushCommands");

    public static Set<String> FORCE_FLUX_RESULT = LettuceSets.unmodifiableSet("eval", "evalsha", "evalReadOnly",
            "evalshaReadOnly", "fcall", "fcallReadOnly", "dispatch");

    public static Set<String> VALUE_WRAP = LettuceSets.unmodifiableSet("geopos", "bitfield");

    private static final Map<String, String> RESULT_SPEC;

    static {

        Map<String, String> resultSpec = new HashMap<>();
        resultSpec.put("geopos", "Flux<Value<GeoCoordinates>>");
        resultSpec.put("aclCat()", "Mono<Set<AclCategory>>");
        resultSpec.put("aclCat(AclCategory category)", "Mono<Set<CommandType>>");
        resultSpec.put("aclGetuser", "Mono<List<Object>>");
        resultSpec.put("bitfield", "Flux<Value<Long>>");
        resultSpec.put("hgetall", "Flux<KeyValue<K, V>>");
        resultSpec.put("zmscore", "Mono<List<Double>>"); // Redis returns null if element was not found
        resultSpec.put("hgetall(KeyValueStreamingChannel<K, V> channel, K key)", "Mono<Long>");

        RESULT_SPEC = resultSpec;
    }

    protected Consumer<MethodDeclaration> methodMutator() {
        return method -> {

            if (isStreamingChannelMethod(method)) {
                if (!method.getAnnotationByClass(Deprecated.class).isPresent()) {
                    method.addAnnotation(new MarkerAnnotationExpr("Deprecated"));
                }
            }

            if (method.getNameAsString().equals("dispatch")) {

                Parameter output = method.getParameterByName("output").get();
                output.setType("CommandOutput<K, V, ?>");
            }
        };
    }

    protected boolean isStreamingChannelMethod(MethodDeclaration method) {
        return method.getParameters().stream().anyMatch(p -> p.getType().asString().contains("StreamingChannel"));
    }

    /**
     * Mutate type comment.
     *
     * @return
     */
    Function<String, String> commentMutator() {
        return s -> s.replaceAll("\\$\\{intent\\}", "Reactive executed commands").replaceAll("@since 3.0", "@since 4.0")
                + "* @generated by " + getClass().getName() + "\r\n ";
    }

    BiFunction<MethodDeclaration, Comment, Comment> methodCommentMutator() {
        return (method, comment) -> {
            String commentText = comment != null ? comment.getContent() : null;

            if (commentText != null) {

                commentText = commentText.replaceAll("List&lt;(.*)&gt;", "$1").replaceAll("Set&lt;(.*)&gt;", "$1");

                if (isStreamingChannelMethod(method)) {
                    commentText += "* @deprecated since 6.0 in favor of consuming large results through the {@link org.reactivestreams.Publisher} returned by {@link #"
                            + method.getNameAsString() + "}.";
                }

                comment.setContent(commentText);
            }
            return comment;
        };
    }

    /**
     * Mutate type to async result.
     *
     * @return
     */
    Function<MethodDeclaration, Type> methodTypeMutator() {
        return method -> {

            ClassOrInterfaceDeclaration declaringClass = (ClassOrInterfaceDeclaration) method.getParentNode().get();

            String baseType = "Mono";
            String typeArgument = method.getType().toString().trim();

            String fixedResultType = getResultType(method, declaringClass);
            if (fixedResultType != null) {
                return new ClassOrInterfaceType(fixedResultType);
            } else if (CompilationUnitFactory.contains(FORCE_FLUX_RESULT, method)) {
                baseType = "Flux";
            } else if (typeArgument.startsWith("List<")) {
                baseType = "Flux";
                typeArgument = typeArgument.substring(5, typeArgument.length() - 1);
            } else if (typeArgument.startsWith("Set<")) {
                baseType = "Flux";
                typeArgument = typeArgument.substring(4, typeArgument.length() - 1);
            } else {
                baseType = "Mono";
            }

            if (fixedResultType == null && CompilationUnitFactory.contains(VALUE_WRAP, method)) {
                typeArgument = String.format("Value<%s>", typeArgument);
            }

            return CompilationUnitFactory.createParametrizedType(baseType, typeArgument);
        };
    }

    private String getResultType(MethodDeclaration method, ClassOrInterfaceDeclaration classOfMethod) {

        String declaration = nameAndParameters(method);
        if (RESULT_SPEC.containsKey(declaration)) {
            return RESULT_SPEC.get(declaration);
        }

        if (RESULT_SPEC.containsKey(method.getNameAsString())) {
            return RESULT_SPEC.get(method.getNameAsString());
        }

        String key = classOfMethod.getNameAsString() + "." + method.getNameAsString();

        if (RESULT_SPEC.containsKey(key)) {
            return RESULT_SPEC.get(key);
        }

        return null;
    }

    /**
     * Supply additional imports.
     *
     * @return
     */
    Supplier<List<String>> importSupplier() {
        return () -> Arrays.asList("reactor.core.publisher.Flux", "reactor.core.publisher.Mono");
    }

    @ParameterizedTest
    @MethodSource("arguments")
    @Tag(API_GENERATOR)
    void createInterface(String argument) throws Exception {
        createFactory(argument).createInterface();
    }

    static List<String> arguments() {
        return Arrays.asList(Constants.TEMPLATE_NAMES);
    }

    private CompilationUnitFactory createFactory(String templateName) {
        String targetName = templateName.replace("Commands", "ReactiveCommands");
        File templateFile = new File(Constants.TEMPLATES, "io/lettuce/core/api/" + templateName + ".java");
        String targetPackage;

        if (templateName.contains("RedisSentinel")) {
            targetPackage = "io.lettuce.core.sentinel.api.reactive";
        } else {
            targetPackage = "io.lettuce.core.api.reactive";
        }

        CompilationUnitFactory factory = new CompilationUnitFactory(templateFile, Constants.SOURCES, targetPackage, targetName,
                commentMutator(), methodTypeMutator(), methodMutator(), methodDeclaration -> true, importSupplier(), null,
                methodCommentMutator());
        factory.keepMethodSignaturesFor(KEEP_METHOD_RESULT_TYPE);
        return factory;
    }

    static String nameAndParameters(MethodDeclaration method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getName());
        sb.append("(");
        boolean firstParam = true;
        for (Parameter param : method.getParameters()) {
            if (firstParam) {
                firstParam = false;
            } else {
                sb.append(", ");
            }
            sb.append(param.toString(new PrettyPrinterConfiguration().setPrintComments(false)));
        }
        sb.append(")");
        return sb.toString();
    }

}
