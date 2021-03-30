(ns acl-test
  (:require
    [acl]
    [acl-fixture]
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]))

(def test_member "611d7f8a-456d-4f3c-802d-4d869dcd89bf")
(def test_admin "b651939c-96e6-4fbb-88fb-299e728e21c8")
(def test_suspended_admin "b01fae53-d742-4990-ac01-edadeb4f2e8f")
(def test_staff "75c0d9b2-2c23-41a7-93a1-d1b716cdfa6c")

(defn delete-posts
  ([]
   (-> {:url                  "http://localhost:3000/posts"
        :headers              {"Authorization" test_admin}
        :unexceptional-status (constantly true)
        :method               :delete}
       http/request))
  ([user id]
   (-> {:url                  "http://localhost:3000/posts"
        :headers              {"Authorization" user}
        :query-params         {:id id}
        :unexceptional-status (constantly true)
        :method               :delete}
       http/request)))

(defn fetch-posts
  ([user id]
   (-> {:url                  "http://localhost:3000/posts"
        :headers              {"Authorization" user}
        :unexceptional-status (constantly true)
        :query-params         {:id id}
        :method               :get}
       http/request))
  ([user]
   (-> {:url                  "http://localhost:3000/posts"
        :headers              {"Authorization" user}
        :unexceptional-status (constantly true)
        :method               :get}
       http/request))
  ([]
   (-> {:url                  "http://localhost:3000/posts"
        :headers              {"Authorization" test_admin}
        :unexceptional-status (constantly true)
        :method               :get}
       http/request)))

(defn new-post
  ([content]
   (-> {:url                  "http://localhost:3000/posts"
        :headers              {"Authorization" test_admin}
        :unexceptional-status (constantly true)
        :form-params          {:content content}
        :method               :put}
       http/request))
  ([user content]
   (-> {:url                  "http://localhost:3000/posts"
        :headers              {"Authorization" user}
        :unexceptional-status (constantly true)
        :form-params          {:content content}
        :method               :put}
       http/request)))

(defn update-post
  [user id content]
  (-> {:url                  "http://localhost:3000/posts"
       :headers              {"Authorization" user}
       :unexceptional-status (constantly true)
       :form-params          {:content content}
       :query-params         {:id id}
       :method               :post}
      http/request))

(defn post-ids
  [body]
  (map :id (-> body
               (json/read-str :key-fn keyword)
               :data
               :db-data)))

(defn all-post-ids
  []
  (-> (acl-test/fetch-posts)
      :body
      post-ids))

(defn update-count
  [body]
  (println "body:" body)
  (-> body
      (json/read-str :key-fn clojure.core/keyword)
      (get-in [:data :db-data 0 :update-count])))

(use-fixtures :once acl-fixture/std-system-fixture)

(defn init-db-with-two-posts
  []
  (delete-posts)
  (new-post "Test post")
  (new-post "Second Test post"))

(deftest guest-can-read-posts
  (init-db-with-two-posts)
  (let [orig-ids (all-post-ids)]
    (is (= [(count orig-ids) orig-ids]
           (-> {:url                  "http://localhost:3000/posts"
                :unexceptional-status (constantly true)
                :method               :get}
               http/request
               :body
               post-ids
               ((juxt count identity)))) "Guest can read all posts")
    (is (= [1 (first orig-ids)]
           (-> {:url                  "http://localhost:3000/posts"
                :unexceptional-status (constantly true)
                :query-params         {:id (first orig-ids)}
                :method               :get}
               http/request
               :body
               post-ids
               ((juxt count first)))) "Guest can read post by id")))

(deftest guest-cannot-delete-posts
  (init-db-with-two-posts)
  (let [orig-ids (all-post-ids)]
    (is (= [401 "You don't have rights to do this"]
           (-> {:url                  "http://localhost:3000/posts"
                :unexceptional-status (constantly true)
                :method               :delete}
               http/request
               ((juxt :status :body)))) "Guest cannot delete all posts")
    (is (= [401 "You don't have rights to do this"]
           (-> {:url                  "http://localhost:3000/posts"
                :unexceptional-status (constantly true)
                :query-params         {:id (first orig-ids)}
                :method               :delete}
               http/request
               ((juxt :status :body)))) "Guest cannot delete post by id")))

(deftest guest-cannot-create-post
  (init-db-with-two-posts)
  (is (= [401 "You don't have rights to do this"]
         (-> {:url                  "http://localhost:3000/posts"
              :unexceptional-status (constantly true)
              :form-params          {:content "It doesn't save anyway"}
              :method               :put}
             http/request
             ((juxt :status :body)))) "Guest cannot create new post"))

(deftest guest-cannot-update-post
  (init-db-with-two-posts)
  (let [orig-ids (all-post-ids)]
    (is (= [401 "You don't have rights to do this"]
           (-> {:url                  "http://localhost:3000/posts"
                :unexceptional-status (constantly true)
                :query-params         {:id (first orig-ids)}
                :form-params          {:content "It doesn't save anyway"}
                :method               :post}
               http/request
               ((juxt :status :body)))) "Guest cannot update post")))

(deftest member-can-read-posts
  (init-db-with-two-posts)
  (let [orig-ids (all-post-ids)]
    (is (= [(count orig-ids) orig-ids] (-> (fetch-posts test_member)
                                           :body
                                           post-ids
                                           ((juxt count identity)))) "Member can read all posts")
    (is (= [1 (first orig-ids)] (-> (fetch-posts test_member (first orig-ids))
                                    :body
                                    post-ids
                                    ((juxt count first)))) "Member can read all posts")))

(deftest member-can-create-post
  (init-db-with-two-posts)
  (let [orig-ids (all-post-ids)]
    (is (= 1 (-> (new-post test_member "It will be stored")
                 :body
                 update-count)) "Member can create post")
    (is (= (inc (count orig-ids)) (count (all-post-ids))))))

(deftest member-can-delete-own-post
  (delete-posts)
  (is (= 1 (-> (new-post test_member "Something to delete")
               :body
               update-count)))
  (is (= 1 (-> (delete-posts test_member (first (all-post-ids)))
               :body
               update-count)))
  (is (empty? (all-post-ids))))

(deftest member-cannot-delete-others-post
  (delete-posts)
  (is (= 1 (-> (new-post test_staff "Something to delete")
               :body
               update-count)))
  (is (= 0 (-> (delete-posts test_member (first (all-post-ids)))
               :body
               update-count)))
  (is (= 1 (count (all-post-ids)))))

(deftest member-can-update-own-post
  (delete-posts)
  (is (= 1 (-> (new-post test_member "Something to delete")
               :body
               update-count)))
  (is (= 1 (-> (update-post test_member (first (all-post-ids)) "Or update instead")
               :body
               update-count)))
  (is (= 1 (count (all-post-ids))))
  (is (.contains (:body (fetch-posts)) "Or update instead")))

(deftest member-cannot-update-others-post
  (delete-posts)
  (is (= 1 (-> (new-post test_staff "Something to delete")
               :body
               update-count)))
  (is (= 0 (-> (update-post test_member (first (all-post-ids)) "Or update instead")
               :body
               update-count)))
  (is (= 1 (count (all-post-ids))))
  (is (.contains (:body (fetch-posts)) "Something to delete")))
