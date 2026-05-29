package io.github.manormachine2207.hrsuite.antragstyp.version;

import io.github.manormachine2207.hrsuite.antragstyp.form.FormDefinition;
import io.github.manormachine2207.hrsuite.antragstyp.form.FormField;
import io.github.manormachine2207.hrsuite.antragstyp.form.Option;
import io.github.manormachine2207.hrsuite.antragstyp.form.Validation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reine Funktion: klassifiziert einen Definition-Diff als MINOR (in-place) oder MAJOR
 * (neuer Major) gemaess ADR-009. Seiteneffektfrei, kein Spring-State noetig
 * (als {@code @Component} registriert fuer Constructor-Injection in den Service).
 */
@Component
public class CompatibilityClassifier {

    public ChangeClassification classify(
            FormDefinition oldForm,
            FormDefinition newForm,
            String oldWorkflowBpmn,
            String newWorkflowBpmn,
            Map<String, Object> oldSfBindings,
            Map<String, Object> newSfBindings) {

        List<String> major = new ArrayList<>();
        List<String> minor = new ArrayList<>();

        Map<String, FormField> oldFields = byKey(oldForm);
        Map<String, FormField> newFields = byKey(newForm);

        for (String key : oldFields.keySet()) {
            if (!newFields.containsKey(key)) {
                major.add("field removed: " + key);
            }
        }
        for (Map.Entry<String, FormField> e : newFields.entrySet()) {
            FormField oldF = oldFields.get(e.getKey());
            if (oldF == null) {
                if (e.getValue().required()) {
                    major.add("new required field: " + e.getKey());
                } else {
                    minor.add("optional field added: " + e.getKey());
                }
            } else {
                classifyField(oldF, e.getValue(), major, minor);
            }
        }

        if (!Objects.equals(norm(oldWorkflowBpmn), norm(newWorkflowBpmn))) {
            major.add("workflow/BPMN changed");
        }
        Map<String, Object> effOldSf = oldSfBindings == null ? Map.of() : oldSfBindings;
        Map<String, Object> effNewSf = newSfBindings == null ? Map.of() : newSfBindings;
        if (!Objects.equals(effOldSf, effNewSf)) {
            major.add("sf_action_bindings changed");
        }

        return major.isEmpty()
                ? new ChangeClassification(ChangeClass.MINOR, List.copyOf(minor))
                : new ChangeClassification(ChangeClass.MAJOR, List.copyOf(major));
    }

    private void classifyField(FormField o, FormField n, List<String> major, List<String> minor) {
        String k = n.key();
        if (o.type() != n.type()) {
            major.add("field type changed: " + k);
            return; // Typwechsel dominiert; Sub-Diffs sind dann bedeutungslos
        }
        if (!o.required() && n.required()) {
            major.add("field made required: " + k);
        } else if (o.required() && !n.required()) {
            minor.add("field made optional: " + k);
        }
        classifyValidation(k, o.validation(), n.validation(), major, minor);
        classifyOptions(k, o.options(), n.options(), major, minor);
        if (!Objects.equals(o.label(), n.label())) {
            minor.add("label changed: " + k);
        }
        if (!Objects.equals(o.helpText(), n.helpText())) {
            minor.add("help text changed: " + k);
        }
        if (!Objects.equals(o.defaultValue(), n.defaultValue())) {
            minor.add("default value changed: " + k);
        }
    }

    private void classifyValidation(String k, Validation oIn, Validation nIn,
                                    List<String> major, List<String> minor) {
        Validation o = oIn == null ? new Validation(null, null, null) : oIn;
        Validation n = nIn == null ? new Validation(null, null, null) : nIn;
        // maxLength + max sind Obergrenzen (value <= bound)
        upperBound("maxLength", k, o.maxLength(), n.maxLength(), major, minor);
        upperBound("max", k, o.max(), n.max(), major, minor);
        // min ist Untergrenze (value >= bound)
        lowerBound("min", k, o.min(), n.min(), major, minor);
    }

    private void upperBound(String name, String k, Integer oldB, Integer newB,
                            List<String> major, List<String> minor) {
        if (tighter(oldB, newB, true)) {
            major.add(name + " tightened: " + k);
        } else if (looser(oldB, newB, true)) {
            minor.add(name + " loosened: " + k);
        }
    }

    private void lowerBound(String name, String k, Integer oldB, Integer newB,
                            List<String> major, List<String> minor) {
        if (tighter(oldB, newB, false)) {
            major.add(name + " tightened: " + k);
        } else if (looser(oldB, newB, false)) {
            minor.add(name + " loosened: " + k);
        }
    }

    /** Eine Schranke wird strenger: neue Schranke gesetzt, wo vorher keine war,
     *  oder enger als zuvor. {@code upper}=true ⇒ Obergrenze (kleiner=strenger). */
    private boolean tighter(Integer oldB, Integer newB, boolean upper) {
        if (newB == null) return false;
        if (oldB == null) return true;
        return upper ? newB < oldB : newB > oldB;
    }

    /** Eine Schranke wird gelockert: bestehende Schranke entfernt oder geweitet. */
    private boolean looser(Integer oldB, Integer newB, boolean upper) {
        if (oldB == null) return false;
        if (newB == null) return true;
        return upper ? newB > oldB : newB < oldB;
    }

    private void classifyOptions(String k, List<Option> oList, List<Option> nList,
                                 List<String> major, List<String> minor) {
        Map<String, Option> o = optByValue(oList);
        Map<String, Option> n = optByValue(nList);
        for (String v : o.keySet()) {
            if (!n.containsKey(v)) {
                major.add("select option value removed: " + k + "/" + v);
            }
        }
        for (Map.Entry<String, Option> e : n.entrySet()) {
            Option oo = o.get(e.getKey());
            if (oo == null) {
                minor.add("select option value added: " + k + "/" + e.getKey());
            } else if (!Objects.equals(oo.label(), e.getValue().label())) {
                minor.add("select option label changed: " + k + "/" + e.getKey());
            }
        }
    }

    private static Map<String, FormField> byKey(FormDefinition def) {
        Map<String, FormField> m = new LinkedHashMap<>();
        if (def != null) {
            for (FormField f : def.fields()) {
                m.put(f.key(), f);
            }
        }
        return m;
    }

    private static Map<String, Option> optByValue(List<Option> opts) {
        Map<String, Option> m = new LinkedHashMap<>();
        if (opts != null) {
            for (Option o : opts) {
                m.put(o.value(), o);
            }
        }
        return m;
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim();
    }
}
