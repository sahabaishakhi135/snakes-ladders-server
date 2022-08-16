(ns snakesladders.board.snakesladders.events)

(defn ready []
  {:type :ready})

(defn winner [player-id color]
  {:type :win
   :data {:color color, :player player-id}})

(defn next-stage [stage]
  {:type :next-stage
   :data {:stage stage}})

(defn next-turn [player-id color move]
  {:type :next-turn
   :data {:color color, :move move, :player player-id}})

(defn die-roll [color n nums]
  {:type :roll-die-result
   :data {:color color, :roll n, :numRollsLeft nums}})

(defn pre-roll [player-id color nums num-rolls]
  {:type :pre-roll-result
   :data {:color color, :rolls nums, :player player-id, :numRollsLeft num-rolls}})

(defn move-bead
  [bead cell new-cell]
  {:type :move-bead-result
   :data {:bead bead, :from cell, :to new-cell}})

(defn sync-board
  [beads used rolls nums scores turn-order remaining]
  {:type :sync-board
   :data {:beads (seq beads), :rolled used, :rollsLeft rolls :numRollsLeft nums :scores scores :turn-order turn-order :remaining remaining}})

(defn update-score
  ([player-id color points score extra-roll]
   {:type :score-update
    :data {:player player-id, :color color, :points points, :score score, :extraRoll extra-roll}})
  ([player-id color points score]
    {:type :score-update
     :data {:player player-id, :color color, :points points, :score score}}))

(defn kill-bead [bead]
  {:type :kill-bead
   :data {:bead bead}})

(defn chat
  [from to message]
  {:type :chat-result
   :data {:from from :to to :message message}})

(defn end-round
  []
  {:type :end-round})

(defn snake [bead from to]
  {:type :snake-result
   :data {:bead bead :from from :to to}})

(defn ladder [bead from to]
  {:type :ladder-result
   :data {:bead bead :from from :to to}})