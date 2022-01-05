# normalli
Data normalization driven by [Malli](https://github.com/metosin/malli) schemas.

## What it does

Normalli normalizes data structures by replacing *entities*
with references to entries in an entity register.

*Entities* are identified by a [Malli schema type](https://cljdoc.org/d/metosin/malli/0.6.2/api/malli.core#type)
and optionally by a predicate to be applied on the subsets of the input data
which satisfy the schema type.

## Status

Experimental.

## Usage

```clojure
(:require [malli.core :as m]
          [normalli.core :as nm])
```

TODO: add basic usage examples.

With a more complicated schema (taken from [the Hiccup example in Malli README](https://github.com/metosin/malli#parsing-values)):

```Clojure
(def Hiccup
  [:schema {:registry {"hiccup" [:orn
                                 [:node [:catn
                                         [:name keyword?]
                                         [:props [:? [:map-of keyword? any?]]]
                                         [:children [:* [:schema [:ref "hiccup"]]]]]]
                                 [:primitive [:orn
                                              [:nil nil?]
                                              [:boolean boolean?]
                                              [:number number?]
                                              [:text string?]]]]}}
   "hiccup"])
```

```Clojure
(= (nm/normalize {:schema Hiccup
                  :entities {:paragraphs {:schema-type :catn
                                          :pred #(= (first %) :p)
                                          :pk-fn (comp :id second)}}}
                 [:div {:id "main", :class [:foo :bar]}
                  [:div {:id "title", :class [:header]}
                   [:p {:id "p1"} "Hello, world of data"]
                   [:p {:id "p2"}
                    "This is another paragraph"
                    [:p {:id "p3"} "This is nested paragraph"]]]])

   #:normalli.core{:reg {:paragraphs {"p1" [:p {:id "p1"} "Hello, world of data"],
                                      "p3" [:p {:id "p3"} "This is nested paragraph"],
                                      "p2" [:p
                                            {:id "p2"}
                                            "This is another paragraph"
                                            #:normalli.core{:ref [:paragraphs "p3"]}]}},
                   :val [:div
                         {:id "main", :class [:foo :bar]}
                         [:div
                          {:id "title", :class [:header]}
                          #:normalli.core{:ref [:paragraphs "p1"]}
                          #:normalli.core{:ref [:paragraphs "p2"]}]]})
;; => true
```

## Internals

Implementation is based on [Malli transformers](https://github.com/metosin/malli#value-transformation).

## TODO
- test the README with https://github.com/lread/test-doc-blocks
- run tests in cljs
- add license
- add changelog file
- add perf tests
- write decoders and add the corresponding tests
- add more base case tests
- move failed attempts to a separate document, see: https://github.com/lread/test-doc-blocks/blob/main/doc/design-notes.adoc
- add CI with GitHubActions and / or CircleCI
- add badges: Build, clojars, cljdoc
