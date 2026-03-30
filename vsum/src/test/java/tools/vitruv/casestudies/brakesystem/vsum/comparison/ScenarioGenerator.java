package tools.vitruv.casestudies.brakesystem.vsum.comparison;

import static tools.vitruv.casestudies.brakesystem.vsum.comparison.ThreeModelScenarioSetup.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.eclipse.jgit.api.Git;

import brakesystem.ABSSensor;
import brakesystem.BrakeCaliper;
import brakesystem.BrakeDisk;
import brakesystem.BrakeHose;
import brakesystem.BrakePad;

import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/**
 * Generates a {@link ThreeModelScenarioSetup.PreparedScenario} from a {@link ScenarioConfig}.
 * Uses seeded randomness for full reproducibility.
 *
 * <p>The generator creates a git repo with a base state, then generates
 * multi-commit branch histories with controlled overlap and reaction-trigger density.
 */
public class ScenarioGenerator {

    /** Component types cycled through when creating the base state. */
    private static final Class<?>[] COMPONENT_TYPES = {
            BrakeDisk.class, BrakePad.class, ABSSensor.class, BrakeCaliper.class
    };

    /** Monotonic counter ensuring each action produces a unique value. */
    private int actionCounter = 0;

    /**
     * Generates a complete scenario with git repo, branches, and changelogs.
     */
    public ThreeModelScenarioSetup.PreparedScenario generate(ScenarioConfig config, Path tempDir) throws Exception {
        Random rng = new Random(config.seed());
        actionCounter = 0;

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);

            // 1. Create base state
            addBrakesystem(vsum, tempDir);
            Map<String, Class<?>> basePool = createBaseComponents(vsum, config.baseComponentCount(), rng);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: " + config.baseComponentCount() + " components").call();

            // 2. Partition base elements for overlap control
            List<String> allIds = new ArrayList<>(basePool.keySet());
            List<String> focusA = selectFocus(allIds, config.overlapFraction(), true);
            List<String> focusB = selectFocus(allIds, config.overlapFraction(), false);

            // 3. Generate branch A (feature) commits
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();

            // Branch A gets its own copy of the pool (additions don't affect B)
            Map<String, Class<?>> poolA = new LinkedHashMap<>(basePool);
            int addCounterA = 0;
            for (int c = 0; c < config.commitsPerBranchA(); c++) {
                var capture = freshCapture(vsum);
                int actionsPerCommit = 1 + rng.nextInt(3); // 1-3 actions
                boolean anyActionExecuted = false;
                for (int a = 0; a < actionsPerCommit; a++) {
                    int prevCounter = addCounterA;
                    addCounterA = executeRandomAction(vsum, poolA, focusA,
                            config.reactionTriggerFraction(), rng, "A", addCounterA);
                    anyActionExecuted = true;
                }
                if (anyActionExecuted) {
                    commitWithChangelog(git, capture, tempDir, "feature",
                            "feature-commit-" + (c + 1));
                } else {
                    vsum.removeChangePropagationListener(capture);
                }
            }

            // 4. Switch to main, reload VSUM
            git.checkout().setName("main").call();
            vsum.reload();

            // 5. Generate branch B (main) commits — fresh pool from base only
            Map<String, Class<?>> poolB = new LinkedHashMap<>(basePool);
            int addCounterB = 0;
            for (int c = 0; c < config.commitsPerBranchB(); c++) {
                var capture = freshCapture(vsum);
                int actionsPerCommit = 1 + rng.nextInt(3);
                boolean anyActionExecuted = false;
                for (int a = 0; a < actionsPerCommit; a++) {
                    addCounterB = executeRandomAction(vsum, poolB, focusB,
                            config.reactionTriggerFraction(), rng, "B", addCounterB);
                    anyActionExecuted = true;
                }
                if (anyActionExecuted) {
                    commitWithChangelog(git, capture, tempDir, "main",
                            "main-commit-" + (c + 1));
                } else {
                    vsum.removeChangePropagationListener(capture);
                }
            }

            vsum.dispose();
        }

        return new ThreeModelScenarioSetup.PreparedScenario(tempDir, "feature", "main");
    }

    /**
     * Creates base components with round-robin type assignment.
     * Returns a map of component ID -> component type.
     */
    private Map<String, Class<?>> createBaseComponents(InternalVirtualModel vsum, int count, Random rng) {
        Map<String, Class<?>> pool = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            Class<?> type = COMPONENT_TYPES[i % COMPONENT_TYPES.length];
            String id = typePrefix(type) + (i + 1);
            createComponent(vsum, type, id, rng);
            pool.put(id, type);
        }
        return pool;
    }

    /**
     * Selects a focus subset of element IDs for a branch based on overlap fraction.
     * When overlap=0.0, sets are disjoint; when overlap=1.0, sets are identical.
     */
    static List<String> selectFocus(List<String> allIds, double overlapFraction, boolean isBranchA) {
        int n = allIds.size();
        if (n <= 1) return new ArrayList<>(allIds);

        // Size of each focus set: always at least 1
        int focusSize = Math.max(1, (int) Math.ceil(n * (0.5 + overlapFraction / 2.0)));
        focusSize = Math.min(focusSize, n);

        if (isBranchA) {
            return new ArrayList<>(allIds.subList(0, focusSize));
        } else {
            return new ArrayList<>(allIds.subList(n - focusSize, n));
        }
    }

    /**
     * Executes a random model action on an element from the focus set.
     * Returns updated add counter for unique ID generation.
     */
    private int executeRandomAction(InternalVirtualModel vsum, Map<String, Class<?>> componentPool,
            List<String> focusIds, double reactionTriggerFraction, Random rng,
            String branchLabel, int addCounter) {

        // 20% chance of additive action (adding new component)
        if (rng.nextDouble() < 0.2) {
            addCounter++;
            String newId = branchLabel.toLowerCase() + "-new-" + addCounter;
            Class<?> type = COMPONENT_TYPES[rng.nextInt(COMPONENT_TYPES.length)];
            createComponent(vsum, type, newId, rng);
            componentPool.put(newId, type);
            return addCounter;
        }

        // Pick a target element from focus set
        String targetId = focusIds.get(rng.nextInt(focusIds.size()));
        Class<?> targetType = componentPool.get(targetId);
        if (targetType == null) {
            // Element was removed or doesn't exist; skip
            return addCounter;
        }

        // Decide reaction-triggering vs non-triggering
        boolean useReactionTriggering = rng.nextDouble() < reactionTriggerFraction;
        List<ModelAction> candidates = ModelAction.modifyActionsFor(targetType, useReactionTriggering);

        // Fallback: if no candidates for this category, try the other
        if (candidates.isEmpty()) {
            candidates = ModelAction.modifyActionsFor(targetType, !useReactionTriggering);
        }
        // Ultimate fallback: any modify action for this type
        if (candidates.isEmpty()) {
            candidates = ModelAction.allModifyActionsFor(targetType);
        }
        // If still empty, skip
        if (candidates.isEmpty()) {
            return addCounter;
        }

        ModelAction action = candidates.get(rng.nextInt(candidates.size()));
        executeAction(vsum, action, targetId, rng);
        return addCounter;
    }

    /**
     * Creates a new component of the given type with realistic random values.
     */
    private void createComponent(InternalVirtualModel vsum, Class<?> type, String id, Random rng) {
        if (type == BrakeDisk.class) {
            addBrakeDisk(vsum, id, 200 + rng.nextInt(200), rng.nextBoolean(), 15 + rng.nextInt(20));
        } else if (type == BrakePad.class) {
            addBrakePad(vsum, id, 30 + rng.nextInt(50), 60 + rng.nextInt(80), 8 + rng.nextInt(15));
        } else if (type == ABSSensor.class) {
            addABSSensor(vsum, id, 50 + rng.nextInt(150), 2 + rng.nextInt(4));
        } else if (type == BrakeCaliper.class) {
            addBrakeCaliper(vsum, id, 30 + rng.nextInt(30));
        }
    }

    /**
     * Generates a scenario where both branches modify different reaction-triggering
     * attributes of the same elements (e.g., diameter vs thickness of the same BrakeDisk).
     * This creates overlapping consequential footprints (both write thermalLoadRating)
     * without direct conflicts, so our merge succeeds while EMF Compare reports conflicts.
     */
    public ThreeModelScenarioSetup.PreparedScenario generateConsequentialOverlap(
            ScenarioConfig config, Path tempDir) throws Exception {
        Random rng = new Random(config.seed());
        actionCounter = 0;

        try (var git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call()) {
            InternalVirtualModel vsum = createThreeModelVsum(tempDir);

            // 1. Create base state — only BrakeDisk and BrakePad (types with paired attributes)
            addBrakesystem(vsum, tempDir);
            Map<String, Class<?>> basePool = createPairedBaseComponents(vsum, config.baseComponentCount(), rng);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("Base: " + config.baseComponentCount() + " paired components").call();

            List<String> allIds = new ArrayList<>(basePool.keySet());

            // 2. Branch A: modify attribute A of each target (diameter for disks, height for pads)
            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();

            List<String> targets = selectTargets(allIds, config.overlapFraction(), rng);
            for (int c = 0; c < config.commitsPerBranchA(); c++) {
                var capture = freshCapture(vsum);
                String targetId = targets.get(c % targets.size());
                Class<?> type = basePool.get(targetId);
                ModelAction actionA = firstAttributeFor(type);
                executeAction(vsum, actionA, targetId, rng);
                commitWithChangelog(git, capture, tempDir, "feature", "feature-commit-" + (c + 1));
            }

            // 3. Switch to main, reload
            git.checkout().setName("main").call();
            vsum.reload();

            // 4. Branch B: modify attribute B of the same targets (thickness for disks, width for pads)
            for (int c = 0; c < config.commitsPerBranchB(); c++) {
                var capture = freshCapture(vsum);
                String targetId = targets.get(c % targets.size());
                Class<?> type = basePool.get(targetId);
                ModelAction actionB = secondAttributeFor(type);
                executeAction(vsum, actionB, targetId, rng);
                commitWithChangelog(git, capture, tempDir, "main", "main-commit-" + (c + 1));
            }

            vsum.dispose();
        }

        return new ThreeModelScenarioSetup.PreparedScenario(tempDir, "feature", "main");
    }

    /**
     * Creates base components using only types that have paired reaction-triggering
     * attributes (BrakeDisk and BrakePad), alternating between the two.
     */
    private Map<String, Class<?>> createPairedBaseComponents(InternalVirtualModel vsum, int count, Random rng) {
        Class<?>[] pairedTypes = { BrakeDisk.class, BrakePad.class };
        Map<String, Class<?>> pool = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            Class<?> type = pairedTypes[i % pairedTypes.length];
            String id = typePrefix(type) + (i + 1);
            createComponent(vsum, type, id, rng);
            pool.put(id, type);
        }
        return pool;
    }

    /**
     * Selects a subset of target element IDs that both branches will modify.
     * Uses overlapFraction to control how many base elements are targeted.
     */
    private List<String> selectTargets(List<String> allIds, double overlapFraction, Random rng) {
        int count = Math.max(1, (int) Math.ceil(allIds.size() * (0.3 + overlapFraction * 0.7)));
        count = Math.min(count, allIds.size());
        List<String> shuffled = new ArrayList<>(allIds);
        java.util.Collections.shuffle(shuffled, rng);
        return shuffled.subList(0, count);
    }

    /** Returns the first reaction-triggering attribute for a paired type. */
    private static ModelAction firstAttributeFor(Class<?> type) {
        if (type == BrakeDisk.class) return ModelAction.CHANGE_DISK_DIAMETER;
        if (type == BrakePad.class) return ModelAction.CHANGE_PAD_HEIGHT;
        throw new IllegalArgumentException("No paired attribute for " + type.getSimpleName());
    }

    /** Returns the second reaction-triggering attribute for a paired type. */
    private static ModelAction secondAttributeFor(Class<?> type) {
        if (type == BrakeDisk.class) return ModelAction.CHANGE_DISK_THICKNESS;
        if (type == BrakePad.class) return ModelAction.CHANGE_PAD_WIDTH;
        throw new IllegalArgumentException("No paired attribute for " + type.getSimpleName());
    }

    /**
     * Executes a specific model action with random values.
     * Uses a monotonic counter to ensure values always change from the previous value.
     */
    private void executeAction(InternalVirtualModel vsum, ModelAction action, String targetId, Random rng) {
        // Use actionCounter to ensure each call produces a unique value
        int seq = ++actionCounter;
        switch (action) {
            case CHANGE_DISK_DIAMETER ->
                changeBrakeDiskDiameter(vsum, targetId, 500 + seq * 10 + rng.nextInt(5));
            case CHANGE_DISK_THICKNESS ->
                changeBrakeDiskThickness(vsum, targetId, 40 + seq * 2 + rng.nextInt(2));
            case CHANGE_PAD_HEIGHT ->
                changeBrakePadHeight(vsum, targetId, 80 + seq * 5 + rng.nextInt(3));
            case CHANGE_PAD_WIDTH ->
                changeBrakePadWidth(vsum, targetId, 140 + seq * 5 + rng.nextInt(3));
            case CHANGE_DISK_CENTERING_DIAMETER ->
                changeBrakeDiskCenteringDiameter(vsum, targetId, 150 + seq * 5 + rng.nextInt(3));
            case CHANGE_DISK_RIM_HOLE_NUMBER ->
                changeBrakeDiskRimHoleNumber(vsum, targetId, 10 + seq);
            case CHANGE_CALIPER_PISTON_DIAMETER ->
                changeCaliperPistonDiameter(vsum, targetId, 60 + seq * 3 + rng.nextInt(2));
            case CHANGE_SENSOR_LENGTH ->
                changeABSSensorLength(vsum, targetId, 200 + seq * 10 + rng.nextInt(5));
            case CHANGE_SENSOR_PINS ->
                changeABSSensorPins(vsum, targetId, 6 + seq);
            default ->
                throw new IllegalArgumentException("Unexpected action in executeAction: " + action);
        }
    }

    private static String typePrefix(Class<?> type) {
        if (type == BrakeDisk.class) return "disk";
        if (type == BrakePad.class) return "pad";
        if (type == ABSSensor.class) return "sensor";
        if (type == BrakeCaliper.class) return "caliper";
        if (type == BrakeHose.class) return "hose";
        return "comp";
    }
}
