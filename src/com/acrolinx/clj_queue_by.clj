(ns com.acrolinx.clj-queue-by)

;;; Persistent Queue Helpers

(defmethod print-method clojure.lang.PersistentQueue
  [q ^java.io.Writer w]
  (.write w "#<<")
  (print-method (sequence q) w))

(defn- persistent-empty-queue []
  clojure.lang.PersistentQueue/EMPTY)

(defn- persistent-queue
  "Create a persistent queue, optionally with init values.

  If an init value is provided it is added to the queue. For a
  sequential init value, all items are added to the queue separately."
  ([] (persistent-empty-queue))
  ([x]
   (if (sequential? x)
     (reduce conj (persistent-empty-queue) x)
     (conj (persistent-empty-queue) x))))

(defn- quonj
  "Conj x on queue, ensuring q is a persistent queue.

  If q is nil, it is created as a clojure.lang.PersistentQueue and x
  is conj'ed onto it. Otherwise, if q is defined x is just conj'ed."
  [q x]
  (if q (conj q x)
      (persistent-queue x)))

;; This particular queue implementation

(defn- internal-queue
  "Returns internal representation of the queue.

  The first item of this vector is the current snapshot of already
  selected items. Its items are returned on pop until the selected
  queue is empty. Then a new snapshot is taken from the queued items
  in the map which is the second item of this vector. The keys of the
  map are created with the key-fn which is passed to the
  constructor."
  []
  [(persistent-empty-queue) {}])

(defn- queue-count
  "Given a derefed queue, returns a count of all items.

  Sum of items already selected plus all items in the separate
  queues."
  [[selected queued]]
  (reduce (fn [sum [k countable-val]]
            (+ sum (count countable-val)))
          (count selected)
          queued))

(defn persistent-data-queue
  "Extracts the ::data field from all items in sequence s into a
  PersistentQueue."
  [s]
  (persistent-queue (map ::data s)))

(defn- do-deref [the-q]
  (let [[selected queued] @the-q]
    [(persistent-data-queue selected)
     (reduce
      (fn [acc [k pq]]
        (assoc acc k (persistent-data-queue pq)))
      {}
      queued)]))

(defn- queue-push
  "Backend function to perform the push to the queue.

  Throws exception when MAX-SIZE is reached.
  Alters internal queue and index state by side-effect."
  [the-q the-index keyfn max-size it]
  
  (dosync
   (if (< (queue-count @the-q) max-size)
     (do
       (alter the-q update-in [1 (keyfn it)] quonj
              {::data it
               ::id   (inc @the-index)})
       (alter the-index inc))
     (throw (ex-info "Queue overflow."
                     {:item it
                      :current-size (queue-count @the-q)})))))

(defn- pop-from-selected [the-q]
  (let [[selected queued] @the-q
        head (peek selected)
        tail (pop selected)]
    (alter the-q assoc-in [0] tail)
    head))

(defn- peeks-and-pops [queue-map]
  (loop [heads     (persistent-empty-queue)
         tails     {}
         queue-data queue-map]
    (if (seq queue-data)
      (let [[k queue-val] (first queue-data)
            this-head     (peek queue-val)
            this-tail     (pop queue-val)]
        
          (recur (conj heads this-head)
                 (if (empty? this-tail)
                   tails
                   (assoc tails k this-tail)) 
                 (next queue-data)))
      [(persistent-queue (sort-by ::id heads))
       tails])))

(defn- select-snapshot! [the-q]
  (let [[heads tails] (peeks-and-pops (second @the-q))]
    (alter the-q assoc-in [0] heads)
    (alter the-q assoc-in [1] tails)))

;; This is where the hard work is done.
;; Need to transparently take a snapshot of all leading items in all
;; current queues.
;; Then remove those items from the internal queues
(defn- queue-pop [the-q]
  (dosync
   (let [[selected queued] @the-q
         selected-size (count selected)]
     (when (= 0 selected-size)
       (select-snapshot! the-q))
     (::data (pop-from-selected the-q)))))

;; for no particular reason
(def ^:private ^:const DEFAULT-QUEUE-SIZE 128)

(defn queue-by
  ([keyfn]
   (queue-by keyfn DEFAULT-QUEUE-SIZE))
  ([keyfn max-q-size]
   (let [the-q (ref (internal-queue))
         the-index (ref 0)]
     ;;FIXME: defrecord or deftype to be able to override print-method
     (reify

       clojure.lang.Counted
       (count [this]
         (queue-count @the-q))

       clojure.lang.IDeref
       (deref [this] (do-deref the-q))

       clojure.lang.IFn
       ;; zero args: read a value
       (invoke [this]
         (queue-pop the-q))
       ;; one arg: add the value
       (invoke [this it]
         (queue-push the-q the-index keyfn max-q-size it)
         this)))))