(ns mini.app
  (:require [clojure.string :as string]
            [clojure.walk :as walk]
            [gadget.inspector :as inspector]
            [replicant.dom :as r]))

(defonce ^:private !state (atom {:ui/banner-text "An annoying banner"}))

(defn banner-view [{:ui/keys [banner-text]}]
  [:div#banner {:style {:top 0
                        :transition "top 0.25s"}
                :replicant/mounting {:style {:top "-100px"}}
                :replicant/unmounting {:style {:top "-100px"}}}
   [:p banner-text]
   [:button {:on {:click [[:db/dissoc :ui/banner-text]]}} "Dismiss"]])

(defn- edit-view [{:something/keys [draft]}]
  [:div
   [:h2 "Edit"]
   [:form {:on {:submit [[:dom/prevent-default]
                         [:db/assoc :something/saved [:db/get :something/draft]]]}}
    [:span.wrap-input
     [:input#draft {:replicant/on-mount [[:db/assoc :something/draft-input-element :dom/node]]
                    :on {:input [[:db/assoc :something/draft :event/target.value]]}}]
     (when-not (string/blank? draft)
       [:span.icon-right {:on {:click [[:db/assoc :something/draft ""]
                                       [:dom/set-input-text [:db/get :something/draft-input-element] ""]
                                       [:dom/focus-element [:db/get :something/draft-input-element]]]}
                          :title "Clear draft"}
        "⨉"])]
    [:button {:type :submit} "Save draft"]]])

(defn- display-view [{:something/keys [draft saved]}]
  [:div
   [:h2 "On display"]
   [:ul
    [:li {:replicant/key "draft"} "Draft: " draft]
    [:li {:replicant/key "saved"} "Saved: " saved]]])

(defn- main-view [state]
  [:div {:style {:position "relative"}}
   (when (:ui/banner-text state)
     (banner-view state))
   [:h1 "A tiny (and silly) Replicant example"]
   (edit-view state)
   (display-view state)])

(defn- enrich-action-from-event [{:replicant/keys [js-event node]} actions]
  (walk/postwalk
   (fn [x]
     (cond
       (keyword? x)
       (case x
         :event/target.value (-> js-event .-target .-value)
         :dom/node node
         x)
       :else x))
   actions))

(defn- enrich-action-from-state [state action]
  (walk/postwalk
   (fn [x]
     (cond
       (and (vector? x)
            (= :db/get (first x))) (get state (second x))
       :else x))
   action))

(defn- render! [state]
  (r/render
   (js/document.getElementById "app")
   (main-view state)))

(defn- event-handler [{:replicant/keys [^js js-event] :as replicant-data} actions]
  (doseq [action actions]
    (prn "Triggered action" action)
    (let [enriched-action (->> action
                               (enrich-action-from-event replicant-data)
                               (enrich-action-from-state @!state))
          [action-name & args] enriched-action]
      (prn "Enriched action" enriched-action)
      (case action-name
        :dom/prevent-default (.preventDefault js-event)
        :db/assoc (apply swap! !state assoc args)
        :db/dissoc (apply swap! !state dissoc args)
        :dom/set-input-text (set! (.-value (first args)) (second args))
        :dom/focus-element (.focus (first args))
        (prn "Unknown action" action))))
  (render! @!state))

(defn ^{:dev/after-load true :export true} start! []
  (render! @!state))

(defn ^:export init! []
  (inspector/inspect "App state" !state)
  (r/set-dispatch! event-handler)
  (start!))