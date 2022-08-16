(ns snakesladders.spec-test
  (:require
   [clojure.test :refer :all]
   [clojure.spec.alpha :as s]
   [snakesladders.spec]))

(deftest join-cmd-with-mandatory-keys
  (testing "join-"
    (is (s/valid? :snakesladders.spec/join-cmd {:type "snakesladders"
                                        :alias "p1"
                                        :token "asdfasd.asdfasdf.asdfasdfasd"
                                        :tableType "36"
                                        :roomId "78f43229-b8bd-4ecb-8fd8-3be33c9ce583"
                                        :skinId 2}))))

(deftest join-cmd-with-invalid-roomId
  (testing "join-cmd spec"
    (is (not (s/valid? :snakesladders.spec/join-cmd {:type "snakesladders"
                                             :alias "p1"
                                             :token "asdfasd.asdfasdf.asdfasdfasd"
                                             :tableType "36"
                                             :roomId "78f43229-b8bd"
                                             :skinId 2})))))                                        