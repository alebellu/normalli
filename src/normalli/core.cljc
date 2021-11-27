(ns normalli.core
  (:require [malli.core :as m]
            [malli.transform :as mt]
            [malli.util :as mu]))

(def identity-encoder {:leave identity})
(def map-encoder {:leave (fn [x*]
                           {::reg (reduce merge (map ::reg (vals x*)))
                            ::val (reduce-kv (fn [m k v] (assoc m (::val k) (::val v))) {} x*)})})
(def tuple-encoder {:leave (fn [x*]
                             {::reg (reduce merge (map ::reg x*))
                              ::val (mapv ::val x*)})})
(def basic-encoder {:leave (fn [x*] {::reg {} ::val x*})})
#_(def basic-encoder {:compile (fn [schema _] {:leave (fn [x*] {::reg {} ::val x*})})})
(def encoders (apply conj
                     {:map map-encoder
                      :map-of map-encoder
                      :or identity-encoder
                      :orn identity-encoder
                      :tuple tuple-encoder
                      :cat tuple-encoder
                      :catn tuple-encoder
                      :? identity-encoder
                      :+ tuple-encoder
                      :* tuple-encoder
                      :schema identity-encoder
                      :ref identity-encoder}
                     (map
                      vector
                      [:any :keyword :qualified-keyword :symbol :qualified-symbol
                       :string :int :double :boolean :bigdec :uri :uuid
                       'any? 'keyword? 'simple-keyword? 'qualified-keyword?
                       'symbol? 'simple-symbol? 'qualified-symbol?
                       'string? 'integer? 'int? 'pos-int? 'neg-int? 'nat-int? 'zero?
                       'number? 'float? 'double? 'boolean? 'false? 'true?
                       #?@(:clj ['ratio? 'rational? 'decimal?])
                       'uuid? 'inst?]
                      (repeat basic-encoder))))

(defn normalizer-using-thread-local-marker-stack
  [{:keys [schema-key pred pk-fn additional-encoders]}]
  (let [encoders (merge encoders additional-encoders)
        default-encoder identity-encoder]
    (letfn [(build-normalizer [{:keys [push-marker pop-marker]}]
              (mt/transformer
               {:name "normalli"
                :encoders (merge
                           encoders
                           {schema-key {:enter (fn [x]
                                                 (push-marker (or (not pred) (pred x)))
                                                 (let [encoder (or (get encoders schema-key) default-encoder)]
                                                   (if-let [enter-handler (:enter encoder)]
                                                     (enter-handler x)
                                                     x)))
                                        :leave (fn [x*]
                                                 (let [encoder (or (get encoders schema-key) basic-encoder)
                                                       leave-handler (:leave encoder)
                                                       x (if leave-handler (leave-handler x*) x*)]
                                                   (if (pop-marker)
                                                     ;; put the value in the registry and replace it by its pk
                                                     (let [{::keys [reg val]} x
                                                           pk (pk-fn val)]
                                                       {::reg (merge reg {pk val})
                                                        ::val {::ref pk}})
                                                     x)))}})
                :default-encoder default-encoder
                :decoders (merge
                           {schema-key (fn [x] (let [pk (-> x ::val ::ref)]
                                                 ;; get the value from the registry and return it
                                                 (-> x ::reg (get pk))))})
                :default-decoder ::val}))]
      #?(:clj  (with-local-vars [markers (volatile! (list))] ; one marker stack per thread
                 (build-normalizer {:push-marker (bound-fn [m]
                                                   (let [ms (var-get markers)]
                                                     (vswap! ms conj m)))
                                    :pop-marker (bound-fn []
                                                  (let [ms (var-get markers)
                                                        m (peek @ms)]
                                                    (vswap! ms pop)
                                                    m))}))
         :cljs (let [markers (atom (list))]
                 (build-normalizer {:push-marker (fn [m] (swap! markers conj m))
                                    :pop-marker (fn []
                                                  (let [ms @markers
                                                        m (peek ms)]
                                                    (swap! markers pop)
                                                    m))}))))))

(def normalizer normalizer-using-thread-local-marker-stack)

(defn normalize [{:keys [schema] :as options} value]
  (->> (normalizer options) (m/encode schema value)))

(defn denormalize [{:keys [schema] :as options} value]
  (->> (normalizer options) (m/decode schema value)))

;;;
;;; Some failed attempts follow.
;;; Leaving them here (commented out) as a trace of the process that led to
;;; normalizer-using-thread-local-marker-stack, for future reference.
;;;

#_(defn normalizer-using-data-marker
    "This approach does not work because changing the shape of data in the :enter handler
     breaks traversal."
    [schema-key pred pk-fn]
    (mt/transformer
     {:name "normalli"
      :encoders (merge
                 encoders
                 {schema-key {:enter (fn [x]
                                       (if (or (not pred) (pred x))
                                         ;; put the value in the registry and replace it by its pk
                                         (let [pk (pk-fn x)]
                                           {::marker true ;; needed for the leave interceptor
                                            ::reg {pk x}
                                            ::val {::ref pk}})
                                         x))
                              :leave (fn [x]
                                       (if (::marker x)
                                         (dissoc x ::marker)
                                         (when-let [default-handler (:leave (get encoders schema-key))]
                                           (default-handler x))))}})
      :decoders (merge
                 {schema-key (fn [x] (let [pk (-> x ::val ::ref)]
                                       ;; get the value from the registry and return it
                                       (-> x ::reg (get pk))))})
      :default-decoder ::val}))

#_(defn normalizer-using-meta-marker
    "This approach does not work for example for :cat, meta info is lost going from :enter to
     :leave handlers."
    [schema-key pred pk-fn]
    (mt/transformer
     {:name "normalli"
      :encoders (merge
                 encoders
                 {schema-key {:enter (fn [x]
                                       (let [default-enter-handler (:enter (get encoders schema-key))
                                             x (if default-enter-handler (default-enter-handler x) x)]
                                         (if (or (not pred) (pred x))
                                           ;; this entity must be stored in the registry:
                                           ;; leaving a marker for the leave interceptor
                                           (if (instance? clojure.lang.IObj x)
                                             (vary-meta x assoc ::marker true)
                                             x)
                                           x)))
                              :leave (fn [x*]
                                       (let [default-leave-handler (:leave (get encoders schema-key))
                                             x (if default-leave-handler (default-leave-handler x*) x*)]
                                         (if (::marker (meta x))
                                           ;; put the value in the registry and replace it by its pk
                                           (let [{::keys [reg val]} x
                                                 pk (pk-fn val)]
                                             {::reg (merge reg {pk val})
                                              ::val {::ref pk}})
                                           x)))}})
      :decoders (merge
                 {schema-key (fn [x] (let [pk (-> x ::val ::ref)]
                                       ;; get the value from the registry and return it
                                       (-> x ::reg (get pk))))})
      :default-decoder ::val}))

