(ns normalli.core-test
  (:require [clojure.test :refer [deftest is are testing]]
            [malli.core :as m]
            [normalli.core :as nm]))

(def test-schemas
  {:hiccup [:schema {:registry {"node" [:catn
                                        [:name keyword?]
                                        [:props [:? [:map-of keyword? any?]]]
                                        [:children [:* [:schema [:ref "hiccup"]]]]]
                                "hiccup" [:orn
                                          [:node [:ref "node"]]
                                          [:primitive [:orn
                                                       [:nil nil?]
                                                       [:boolean boolean?]
                                                       [:number number?]
                                                       [:text string?]]]]}}
            "hiccup"]})

(deftest normalization
  (testing "Scalars"
    (are [s x] (= {::nm/reg {x x} ::nm/val {::nm/ref x}}
                  (nm/normalize {:schema s, :schema-key s, :pk-fn identity} x))
               :int 1
               'integer? 10
               :boolean true
               'boolean? false
               :string "a"
               'string? "abcd"))
  (testing "Nested data structure with schema refs"
    (is (= {::nm/reg {"p1" [:p {:id "p1"} "Hello, world of data"]
                      "p2" [:p {:id "p2"} "This is another paragraph"]}
            ::nm/val [:div {:id "main", :class [:foo :bar]}
                      [:div {:id "title"}
                       {::nm/ref "p1"}
                       {::nm/ref "p2"}]]}
           (nm/normalize {:schema (:hiccup test-schemas)
                          :schema-key :catn
                          :pred #(= (first %) :p)
                          :pk-fn (comp :id second)}
                         [:div {:id "main", :class [:foo :bar]}
                          [:div {:id "title"}
                           [:p {:id "p1"} "Hello, world of data"]
                           [:p {:id "p2"} "This is another paragraph"]]])))
    (is (= {::nm/reg {"p1" [:p {:id "p1"} "Hello, world of data"]
                      "p2" [:p {:id "p2"}
                            "This is another paragraph"
                            {::nm/ref "p3"}]
                      "p3" [:p {:id "p3"} "This is nested paragraph"]}
            ::nm/val [:div {:id "main", :class [:foo :bar]}
                      [:div {:id "title"}
                       {::nm/ref "p1"}
                       {::nm/ref "p2"}]]}
           (nm/normalize {:schema (:hiccup test-schemas)
                          :schema-key :catn
                          :pred #(= (first %) :p)
                          :pk-fn (comp :id second)}
                         [:div {:id "main", :class [:foo :bar]}
                          [:div {:id "title"}
                           [:p {:id "p1"} "Hello, world of data"]
                           [:p {:id "p2"}
                            "This is another paragraph"
                            [:p {:id "p3"} "This is nested paragraph"]]]])))))

#_(deftest normalization
  (testing "maps"
    (let [[simple-map schema]
          [{:a 1 :b 1.1 :c "a"}
           [:map [:a int?, :b double?, :c string?]]]]
      (is (= [simple-map nil]
             (nm/normalize simple-map schema))))
    (let [[nested-map schema options]
          [{:a 1 :b {:b1 12345, :b2 "a"}}
           [:map [:a int?] [:b ::m]]
           {:schemas {::m [:map [:b1 int?] [:b2 string?]]}
            :keys {::m :b1}}]]
      (is (= [{:a 1 :b [::nm/ref ::m 12345]}
              {::m {12345 {:b1 12345, :b2 "a"}}}]
             (nm/normalize nested-map schema options)))))
  (testing "sets"
    (let [[simple-set schema]
          [#{:a :b :c}
           [:set keyword?]]]
      (is (= [simple-set nil]
             (nm/normalize simple-set schema))))
    (let [[nested-set schema options]
          [{:a 1 :b #{{:b1 12345, :b2 "a"}}}
           [:map [:a int?, :b [:set ::m]]]
           {:schemas {::m [:map [:b1 int?, :b2 string?]]}
            :keys {::m :b1}}]]
      (is (= [{:a 1 :b #{[::nm/ref ::m 12345]}}
              {::m {12345 {:b1 12345, :b2 "a"}}}]
             (nm/normalize nested-set schema options)))))
  (testing "using generative testing"))
