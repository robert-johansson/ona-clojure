(ns ona.event
  "Event representation for temporal reasoning in ONA.

  Events are the fundamental unit of temporal knowledge - they represent
  beliefs or goals that occur at specific times (or eternally)."
  (:require [ona.term :as term]
            [ona.truth :as truth]))

;; Constants from C ONA
(def eternal-occurrence -1)
(def event-belief-distance 20) ;; How recent event needs to be to override eternal

(defrecord Event
  [term           ;; Term - what this event is about
   type           ;; :belief or :goal
   truth          ;; Truth value
   stamp          ;; Evidential base (for now, just ID)
   occurrence-time ;; Long - when it occurred (eternal-occurrence = -1 for eternal)
   creation-time  ;; Long - when the event was created in the system
   processed?     ;; Boolean - has this been used in inference
   input?])       ;; Boolean - was this from external input

(defn eternal?
  "Check if event is eternal (timeless)"
  [event]
  (= (:occurrence-time event) eternal-occurrence))

(defn temporal?
  "Check if event occurs at a specific time"
  [event]
  (not (eternal? event)))

(defn make-event
  "Create a new event.

  Args:
    term - The term this event is about
    type - :belief or :goal
    truth - Truth value (frequency, confidence)
    occurrence-time - When it occurred (eternal-occurrence for eternal)
    creation-time - Current system time

  Options:
    :stamp - Evidential base (default: creation-time as ID)
    :processed? - Whether already used (default: false)
    :input? - Whether from external input (default: false)"
  ([term type truth occurrence-time creation-time]
   (make-event term type truth occurrence-time creation-time {}))
  ([term type truth occurrence-time creation-time opts]
   (map->Event
    (merge
     {:term term
      :type type
      :truth truth
      :stamp (:stamp opts creation-time)
      :occurrence-time occurrence-time
      :creation-time creation-time
      :processed? false
      :input? false}
     opts))))

(defn make-belief
  "Create a belief event (observation)"
  ([term truth occurrence-time creation-time]
   (make-belief term truth occurrence-time creation-time {}))
  ([term truth occurrence-time creation-time opts]
   (make-event term :belief truth occurrence-time creation-time opts)))

(defn make-goal
  "Create a goal event (desired state)"
  ([term truth occurrence-time creation-time]
   (make-goal term truth occurrence-time creation-time {}))
  ([term truth occurrence-time creation-time opts]
   (make-event term :goal truth occurrence-time creation-time opts)))

(defn event-priority
  "Calculate event priority for queue ordering.

  Priority is based on:
  - Truth expectation (higher confidence & frequency = higher priority)
  - Input events get slight boost

  This is a simplified version - full ONA uses more complex priority calculation."
  [event]
  (let [base-priority (truth/expectation (:truth event))
        input-boost (if (:input? event) 0.1 0.0)]
    (+ base-priority input-boost)))

(defn project-event
  "Project event truth to a target time using temporal projection.

  Returns new event with projected truth value. Confidence decays exponentially
  with temporal distance: c' = c * β^|Δt|

  Args:
    event - Event to project
    target-time - Time to project to
    decay-factor - β parameter (typically 0.99 from TRUTH_PROJECTION_DECAY)

  Returns:
    New event with projected truth (or original if eternal)"
  [event target-time decay-factor]
  (if (eternal? event)
    event  ;; Eternal events don't decay
    (let [delta-t (Math/abs (- target-time (:occurrence-time event)))
          projected-truth (truth/projection (:truth event) delta-t decay-factor)]
      (assoc event :truth projected-truth))))

(defn select-belief
  "Select appropriate belief for current time from eternal and spike beliefs.

  Logic from C ONA (Concept.c):
  - If spike is recent (within EVENT_BELIEF_DISTANCE), use spike
  - Otherwise use eternal belief (if it exists)
  - Project whichever is selected to current time

  Args:
    eternal-belief - Best eternal belief (or nil)
    spike-belief - Most recent temporal belief (or nil)
    current-time - Current system time
    decay-factor - Temporal projection decay factor

  Returns:
    Selected belief projected to current time (or nil if no beliefs)"
  [eternal-belief spike-belief current-time decay-factor]
  (cond
    ;; No beliefs at all
    (and (nil? eternal-belief) (nil? spike-belief))
    nil

    ;; Only eternal
    (nil? spike-belief)
    eternal-belief

    ;; Only spike
    (nil? eternal-belief)
    (project-event spike-belief current-time decay-factor)

    ;; Both exist - check if spike is recent enough
    :else
    (let [time-since-spike (- current-time (:occurrence-time spike-belief))]
      (if (<= time-since-spike event-belief-distance)
        (project-event spike-belief current-time decay-factor)
        eternal-belief))))

(defn format-event
  "Format event for display/debugging"
  [event]
  (let [term-str (term/format-term (:term event))
        truth-str (truth/format-truth (:truth event))
        type-marker (case (:type event)
                      :belief "."
                      :goal "!"
                      "?")
        time-marker (if (eternal? event)
                      ""
                      (str " :|: @" (:occurrence-time event)))]
    (str term-str type-marker " " truth-str time-marker)))

(defn parse-event-type
  "Parse event type from punctuation marker.

  Returns:
    :belief for '.'
    :goal for '!'
    :question for '?'
    nil for invalid"
  [marker]
  (case marker
    "." :belief
    "!" :goal
    "?" :question
    nil))

(comment
  ;; Example usage:

  ;; Create eternal belief
  (def bird-belief
    (make-belief
     (term/make-term "<bird --> animal>")
     (truth/make-truth 1.0 0.9)
     eternal-occurrence
     1))

  ;; Create temporal belief
  (def bird-seen
    (make-belief
     (term/make-term "<bird --> [seen]>")
     (truth/make-truth 1.0 0.9)
     100  ;; occurred at time 100
     100
     {:input? true}))

  ;; Project to future
  (project-event bird-seen 150 0.99)
  ;; => confidence decays from 0.9 to ~0.6

  ;; Select between eternal and spike
  (select-belief bird-belief bird-seen 110 0.99)
  ;; => Returns spike (recent enough)

  (select-belief bird-belief bird-seen 200 0.99)
  ;; => Returns eternal (spike too old)
  )
