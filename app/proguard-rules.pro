# Project-specific ProGuard rules.
#
# None are needed today: the app uses no reflection of its own (ViewModel factories construct their
# classes directly), Navigation 3 keys are kept in memory rather than serialized, there is no
# kotlinx-serialization, BuildConfig is read as plain constants, enums are restored by `name`, and the
# Word lexicons are read by absolute resource path. Room, DataStore, Yandex Mobile Ads, and RuStore
# bring their own consumer rules.
