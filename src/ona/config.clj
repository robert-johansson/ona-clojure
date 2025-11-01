(ns ona.config
  "Configuration parameters for ONA.
   Based on C ONA src/Config.h

   All parameters are organized by category matching C ONA structure.")

;; =============================================================================
;; Anticipation Parameters
;; =============================================================================

(def anticipation-threshold
  "Truth expectation needed for anticipation"
  0.501)

(def anticipation-confidence
  "Confidence of anticipation failures"
  0.01)

;; =============================================================================
;; Decision Parameters
;; =============================================================================

(def condition-threshold
  "Truth expectation to move on to next component goal in sequence"
  0.501)

(def decision-threshold
  "Desire expectation needed for executions"
  0.501)

(def motor-babbling-chance
  "Motor babbling chance"
  0.2)

(def motor-babbling-suppression-threshold
  "Decisions above this threshold will suppress babbling actions"
  0.55)

;; =============================================================================
;; Attention Parameters
;; =============================================================================

(def belief-event-selections
  "Event selections per cycle for inference"
  1)

(def goal-event-selections
  "Goal event selections per cycle for inference"
  1)

(def event-durability
  "Event priority decay per cycle"
  0.9999)

(def concept-durability
  "Concept priority decay per cycle"
  0.9)

(def min-priority
  "Minimum priority to accept events"
  0)

(def event-belief-distance
  "Occurrence time distance in which case event belief is preferred over eternal"
  20)

(def belief-concept-match-target
  "Amount of belief concepts to select to be matched to the selected event"
  80)

(def concept-threshold-adaptation
  "Adaptation speed of the concept priority threshold to meet the match target"
  0.000001)

(def question-priming
  "Questions concept activation priority"
  0.1)

;; =============================================================================
;; Temporal Compounding Parameters
;; =============================================================================

(def max-sequence-len
  "Maximum length of sequences (C ONA: MAX_SEQUENCE_LEN)"
  3)

(def max-compound-op-len
  "Maximum compound op length (C ONA: MAX_COMPOUND_OP_LEN)"
  1)

(def precondition-consequence-distance
  "Max. occurrence time distance between precondition and consequence"
  event-belief-distance)

(def correlate-outcome-recency
  "Occurrence time distance to now to still correlate an outcome"
  event-belief-distance)

(def max-sequence-timediff
  "Maximum time difference to form sequence between events"
  event-belief-distance)

;; =============================================================================
;; Space Parameters
;; =============================================================================

(def concepts-max
  "Maximum amount of concepts"
  4096)

(def cycling-belief-events-max
  "Maximum amount of belief events attention buffer holds"
  40)

(def cycling-goal-events-max
  "Maximum amount of goal events attention buffer holds"
  400)  ; Fixed to match C ONA - was 20 (20x less capacity)

;; =============================================================================
;; Truth Parameters
;; =============================================================================

(def truth-epsilon
  "Minimum truth difference for revision"
  0.01)

(def truth-projection-decay
  "Truth confidence decay factor for temporal projection"
  0.8)  ; Fixed to match C ONA - was 0.99 causing stale beliefs to persist

;; =============================================================================
;; Default Configuration Map
;; =============================================================================

(def default-config
  "Default configuration map for NAR state initialization.
   Can be overridden at runtime via shell commands or API.

   NOTE: Motor babbling is enabled by default with high initial exploration (90%)
   to support sensorimotor learning experiments (Machine Psychology / conditioning).
   The system learns through:
   1. Motor babbling → random actions generate experience
   2. Temporal induction → forms implications <(context &/ ^op) =/> outcome>
   3. Decision making → selects actions based on learned implications
   4. Anticipation → generates negative evidence when predictions fail"
  {:motor-babbling true  ; ENABLED for sensorimotor learning
   :motor-babbling-chance 0.9  ; 90% exploration initially (Machine Psychology default)
   :decision-threshold decision-threshold
   :truth-projection-decay truth-projection-decay
   :concept-durability concept-durability
   :event-durability event-durability
   :max-sequence-len max-sequence-len
   :max-sequence-timediff max-sequence-timediff
   :event-belief-distance event-belief-distance
   :question-priming question-priming})
