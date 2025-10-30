(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'ona/ona-clojure)
(def version "0.1.0")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file "ona.jar")

(defn clean [_]
  (b/delete {:path "target"})
  (b/delete {:path "classes"})
  (b/delete {:path uber-file}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir
                  :compile-opts {:direct-linking true}})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'ona.shell}))
