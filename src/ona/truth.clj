(ns ona.truth
  "Truth value representation and functions for NAL.
   Based on research/01-data-structures/truth-value.md"
  (:require [clojure.math.numeric-tower :as math]))

;; =============================================================================
;; Constants (from Config.h)
;; =============================================================================

(def ^:const default-frequency 1.0)
(def ^:const default-confidence 0.9)
(def ^:const max-confidence 0.99)
(def ^:const min-confidence 0.01)
(def ^:const evidential-horizon 1.0)

;; =============================================================================
;; Truth Value Record
;; =============================================================================

(defrecord Truth [frequency confidence]
  Object
  (toString [this]
    (format "{%.6f %.6f}" frequency confidence)))

(defn make-truth
  "Create a truth value with validation."
  ([f c]
   {:pre [(<= 0.0 f 1.0) (<= 0.0 c 1.0)]}
   (->Truth (double f) (double c)))
  ([]
   (make-truth default-frequency default-confidence)))

;; =============================================================================
;; Evidence Weight Conversion
;; =============================================================================

(defn w2c
  "Convert evidence weight to confidence.
   c = w / (w + k) where k is evidential horizon"
  ^double [^double w]
  (/ w (+ w evidential-horizon)))

(defn c2w
  "Convert confidence to evidence weight.
   w = k * c / (1 - c)"
  ^double [^double c]
  (/ (* evidential-horizon c) (- 1.0 c)))

;; =============================================================================
;; Expectation
;; =============================================================================

(defn expectation
  "Calculate expectation: c * (f - 0.5) + 0.5
   Used for decision thresholds and priority calculations."
  ^double [{:keys [^double frequency ^double confidence]}]
  (+ (* confidence (- frequency 0.5)) 0.5))

;; =============================================================================
;; Core Truth Functions
;; =============================================================================

(defn revision
  "Combine evidence from two sources about the same statement.
   Based on Truth_Revision in Truth.c"
  [{f1 :frequency c1 :confidence :as v1}
   {f2 :frequency c2 :confidence :as v2}]
  (cond
    (zero? c1) v2
    (zero? c2) v1
    :else
    (let [w1 (c2w c1)
          w2 (c2w c2)
          w (+ w1 w2)
          f (min 1.0 (/ (+ (* w1 f1) (* w2 f2)) w))
          c (min max-confidence (max (max (w2c w) c1) c2))]
      (make-truth f c))))

(defn deduction
  "Forward inference: A, A⇒B ⊢ B
   Based on Truth_Deduction in Truth.c"
  [{f1 :frequency c1 :confidence}
   {f2 :frequency c2 :confidence}]
  (let [f (* f1 f2)
        c (* c1 c2 f)]
    (make-truth f c)))

(defn abduction
  "Infer cause from effect: B, A⇒B ⊢ A
   Based on Truth_Abduction in Truth.c"
  [{f1 :frequency c1 :confidence}
   {f2 :frequency c2 :confidence}]
  (make-truth f2 (w2c (* f1 c1 c2))))

(defn induction
  "Generalization: A, B ⊢ A⇒B
   Based on Truth_Induction in Truth.c"
  [v1 v2]
  (abduction v2 v1))  ; Symmetric to abduction in NAL

(defn intersection
  "Conjunction: A, B ⊢ (A ∧ B)
   Based on Truth_Intersection in Truth.c"
  [{f1 :frequency c1 :confidence}
   {f2 :frequency c2 :confidence}]
  (make-truth (* f1 f2) (* c1 c2)))

(defn negation
  "Flip truth value: ¬A
   Based on Truth_Negation in Truth.c"
  [{:keys [frequency confidence]}]
  (make-truth (- 1.0 frequency) confidence))

;; =============================================================================
;; Temporal Truth Functions
;; =============================================================================

(defn projection
  "Project truth value across time with confidence decay.
   c' = c * decay^|Δt|

   Two arities:
   - [truth delta-t decay-factor] - Direct delta-t
   - [truth original-time target-time decay-factor] - Calculate delta-t

   Based on Truth_Projection in Truth.c"
  ([{:keys [frequency confidence]} delta-t decay-factor]
   (let [new-c (* confidence (math/expt decay-factor delta-t))]
     (make-truth frequency new-c)))
  ([truth original-time target-time decay-factor]
   (if (= original-time :eternal)
     truth
     (let [delta-t (math/abs (- target-time original-time))]
       (projection truth delta-t decay-factor)))))

;; =============================================================================
;; Utilities
;; =============================================================================

(defn parse-truth
  "Parse truth value from string: '{f c}' or explicit values"
  [s]
  (cond
    (nil? s) (make-truth)
    (string? s)
    (let [cleaned (-> s
                      (clojure.string/replace #"[{}]" "")
                      (clojure.string/trim))
          parts (clojure.string/split cleaned #"\s+")]
      (case (count parts)
        0 (make-truth)
        1 (make-truth (Double/parseDouble (first parts)) default-confidence)
        2 (make-truth (Double/parseDouble (first parts))
                      (Double/parseDouble (second parts)))
        (throw (ex-info "Invalid truth value format" {:input s}))))
    :else (throw (ex-info "Invalid truth value" {:input s}))))

(defn format-truth
  "Format truth value for output (matching C ONA format)"
  [{:keys [frequency confidence]}]
  (format "{%.6f %.6f}" frequency confidence))

(defn format-truth-verbose
  "Format truth value verbosely (matching C ONA control info format)"
  [{:keys [frequency confidence]}]
  (format "Truth: frequency=%.6f, confidence=%.6f" frequency confidence))
