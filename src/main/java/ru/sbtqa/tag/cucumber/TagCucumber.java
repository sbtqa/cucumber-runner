package ru.sbtqa.tag.cucumber;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import cucumber.runtime.*;
import cucumber.runtime.Runtime;
import cucumber.runtime.junit.FeatureRunner;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.StepContainer;
import gherkin.formatter.model.Step;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.slf4j.LoggerFactory;
import ru.sbtqa.tag.qautils.i18n.I18N;
import ru.sbtqa.tag.qautils.i18n.I18NRuntimeException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;

/**
 * <p>
 * Classes annotated with {@code @RunWith(TagCucumber.class)} will run a
 * Cucumber Feature with I18N support. The class should be empty without any
 * fields or methods.
 * </p>
 * <p>
 * Cucumber will look for a {@code .feature} file on the classpath, using the
 * same resource path as the annotated class ({@code .class} substituted by
 * {@code .feature}).
 * </p>
 * Additional hints can be given to Cucumber by annotating the class with
 * {@link CucumberOptions}.
 *
 * @see CucumberOptions
 */
public class TagCucumber extends Cucumber {
    
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(TagCucumber.class);
    static final String SECRET_DELIMITER = ":::==!!SECRET!!==:::";
    private static Map<String, StepDefinition> stepDefinitionsByPattern;
    public static CucumberFeature cucumberFeature;
    
    /**
     * Constructor called by JUnit.
     *
     * @param clazz the class with the @RunWith annotation.
     *
     * @throws java.io.IOException                         if there is a problem
     * @throws org.junit.runners.model.InitializationError if there is another problem
     * @throws java.lang.IllegalAccessException            if any reflection error
     */
    @SuppressWarnings("unchecked")
    public TagCucumber(Class clazz) throws InitializationError, IOException, IllegalAccessException {
        super(clazz);
        //
        //        Runtime runtime = (Runtime) FieldUtils.readField(this, "runtime", true);
        //
        //        RuntimeOptions options = (RuntimeOptions) FieldUtils.readField(runtime, "runtimeOptions", true);
        //        List pluginFormatterNames = (List) FieldUtils.readField(options, "pluginFormatterNames", true);
        //
        //        pluginFormatterNames.remove("ru.yandex.qatools.allure.cucumberjvm.AllureReporter");
        //        options.addPlugin(new TagAllureReporter());
        //
        //        FieldUtils.writeField(options, "pluginFormatterNames", pluginFormatterNames, true);
        //        FieldUtils.writeField(runtime, "runtimeOptions", options, true);
        
    }
    
    @Override
    @SuppressWarnings("unchecked")
    protected void runChild(FeatureRunner child, RunNotifier notifier) {
        Map<String, StepDefinition> stepDefinitionsByPatternTranslated = new TreeMap<>();
        
        try {
            Runtime runtime = (Runtime) FieldUtils.readField(this, "runtime", true);
            RuntimeGlue glue = (RuntimeGlue) runtime.getGlue();
            
            cucumberFeature = (CucumberFeature) FieldUtils.readField(child, "cucumberFeature", true);
            stepDefinitionsByPattern = (Map<String, StepDefinition>) FieldUtils.readField(glue, "stepDefinitionsByPattern", true);
            
            StepContainer currentStepContainer = (StepContainer) FieldUtils.readField(cucumberFeature, "currentStepContainer", true);
            List<Step> steps = currentStepContainer.getSteps();
            
            
            // Проставляем префикс в зависимости от пакета с секретом
            for (Map.Entry<String, StepDefinition> stepDefinitionEntry : stepDefinitionsByPattern.entrySet()) {
                
                StepDefinition stepDefinition = stepDefinitionEntry.getValue();
                Method method = (Method) FieldUtils.readField(stepDefinition, "method", true);
                String patternString = stepDefinitionEntry.getKey();
                
                try {
                    Class<?> declaringClass = method.getDeclaringClass();
                    I18N i18n = I18N.getI18n(declaringClass, cucumberFeature.getI18n().getLocale(), I18N.DEFAULT_BUNDLE_PATH);
                    patternString = i18n.get(patternString);
                    String canonicalName = declaringClass.getCanonicalName();
                    String context;
                    if (canonicalName.contains("ru.sbtqa.tag.")) {
                        context = canonicalName.substring("ru.sbtqa.tag.".length(), canonicalName.indexOf('.', "ru.sbtqa.tag.".length()));
                        stepDefinitionsByPatternTranslated.put("^" + context + SECRET_DELIMITER + (patternString.startsWith("^") ? patternString.substring(1) : patternString), stepDefinition);
                        Pattern pattern = Pattern.compile(patternString);
                        FieldUtils.writeField(stepDefinition, "pattern", pattern, true);
                        FieldUtils.writeField(stepDefinition, "argumentMatcher", new JdkPatternArgumentMatcher(pattern), true);
                    }
                    //                    if (stepDefinitionEntry.getKey().startsWith("ru.sbtqa.tag.")) {
                    //                        for (int i = currentStepIndex; i < steps.size(); i++) {
                    //
                    //                            Step step = steps.get(i);
                    //                            Pattern p = Pattern.compile(patternString);
                    //                            if (!step.getName().startsWith("ru.sbtqa.tag.") && p.matcher(step.getName()).matches()) {
                    //                                currentStepIndex++;
                    //                                lastStepContext = stepDefinitionEntry.getKey();
                    //                                steps.set(i, step);
                    //                                FieldUtils.writeField(step, "name", lastStepContext + SECRET_DELIMITER + step.getName(), true);
                    //                            }
                    //                        }
                    //                        FieldUtils.writeField(currentStepContainer, "steps", steps, true);
                    //                        FieldUtils.writeField(cucumberFeature, "currentStepContainer", currentStepContainer, true);
                    //                        patternString = patternString.startsWith("^")
                    //                                ? "^" + lastStepContext + SECRET_DELIMITER + patternString.substring(1)
                    //                                : lastStepContext + SECRET_DELIMITER + patternString;
                    //
                    //                    } else {
                    //                        lastStepContext = "";
                    //                    }
                    //                    Pattern pattern = Pattern.compile(patternString);
                    //                    FieldUtils.writeField(stepDefinition, "pattern", pattern, true);
                    //                    FieldUtils.writeField(stepDefinition, "argumentMatcher", new JdkPatternArgumentMatcher(pattern), true);
                    //                    stepDefinitionsByPatternTranslated.put(patternString, stepDefinition);
                    
                } catch (I18NRuntimeException e) {
                    LOG.debug("There is no bundle for translation class. Writing as is", e);
                    stepDefinitionsByPatternTranslated.put(patternString, stepDefinition);
                }
            }
            
            // Обрабатываем степы
            List<String> matchedStepDefsPatterns = new ArrayList<>();
            Step step;
            for (int i = 0; i < steps.size(); i++) {
                matchedStepDefsPatterns.clear();
                step = steps.get(i);
                
                // Сколько степов водходит
                for (Map.Entry<String, StepDefinition> stringStepDefinitionEntry : stepDefinitionsByPatternTranslated.entrySet()) {
                    if (Pattern.compile(stringStepDefinitionEntry.getValue().getPattern())
                               .matcher(step.getName())
                               .matches()) {
                        matchedStepDefsPatterns.add(stringStepDefinitionEntry.getKey());
                    }
                }
                
                if (matchedStepDefsPatterns.isEmpty()) {
                    throw new RuntimeException();
                }
                
                
                if (matchedStepDefsPatterns.size() == 1) {
                    // Если с секретом, значит это шаг из библиотек наших
                    // если нет, обычный шаг, не трогаем его
                    if (matchedStepDefsPatterns.get(0).contains(SECRET_DELIMITER)) {
                        FieldUtils.writeField(step, "name", getContext(matchedStepDefsPatterns.get(0)) + SECRET_DELIMITER + step
                                .getName(), true);
                    }
                } else {
                    // Здесь конфликты могут возникунуть только м\у нашими либами. Контекст задан. Иначе некорректно.
                    // Берем контекст из предыдущего шага
                    String context = getContext(steps.get(i - 1).getName());
                    for (String matchedStepDefsPattern : matchedStepDefsPatterns) {
                        if (matchedStepDefsPattern.contains(context)) {
                            FieldUtils.writeField(step, "name", context + SECRET_DELIMITER + step.getName(), true);
                        }
                    }
                }
            }
            
            // Записать новые регулярки для степов с секретом
            for (Map.Entry<String, StepDefinition> stringStepDefinitionEntry : stepDefinitionsByPatternTranslated.entrySet()) {
                String key = stringStepDefinitionEntry.getKey();
                        FieldUtils.writeField(stringStepDefinitionEntry.getValue(), "pattern", Pattern
                        .compile(key), true);
                FieldUtils.writeField(stringStepDefinitionEntry.getValue(), "argumentMatcher", new JdkPatternArgumentMatcher(Pattern
                        .compile(key)), true);
            }
            
            FieldUtils.writeField(currentStepContainer, "steps", steps, true);
            FieldUtils.writeField(cucumberFeature, "currentStepContainer", currentStepContainer, true);
            FieldUtils.writeField(child, "cucumberFeature", cucumberFeature, true);
            FieldUtils.writeField(glue, "stepDefinitionsByPattern", stepDefinitionsByPatternTranslated, true);
            FieldUtils.writeField(runtime, "glue", glue, true);
            FieldUtils.writeField(this, "runtime", runtime, true);
            
            super.runChild(child, notifier);
        } catch (IllegalAccessException ex) {
            throw new CucumberException(ex);
        }
    }
    
    private String getContext(String reg) {
        reg = reg.startsWith("^") ? reg.substring(1) : reg;
        String[] split = reg.split(SECRET_DELIMITER);
        if (split.length == 1) {
            return "";
        } else {
            return split[0];
        }
    }
    
    
    private List<StepDefinition> findUniques(Queue<StepDefinition> q) {
        List<StepDefinition> uniques = new ArrayList<>();
        while (q.peek() != null) {
            StepDefinition stepDefinition = q.remove();
            if (!q.contains(stepDefinition)) {
                uniques.add(stepDefinition);
            }
        }
        return uniques;
    }
    
    public static CucumberFeature getFeature() {
        return cucumberFeature;
    }
    
}
