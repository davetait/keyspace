# Keyspace R8 rules.
#
# Everything Android instantiates by name (MainActivity, KeyspaceNotificationListener) is declared in the
# manifest, which R8 keeps automatically. Icon packs and widgets are read through Android APIs by resource
# name, not through reflection on our classes, so no extra keep rules are needed so far.
