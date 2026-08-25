# Third-party data

## JMdict/EDICT

The bundled dictionary (`app/src/main/assets/dictionary/jmdict.db`) is built from
[JMdict](https://www.edrdg.org/wiki/index.php/JMdict-EDICT_Dictionary_Project), via the
English-only "common words" release of
[jmdict-simplified](https://github.com/scriptin/jmdict-simplified) (JSON-converted JMdict, MIT
license for the conversion tooling itself).

This application uses the JMdict dictionary file. This file is the property of the Electronic
Dictionary Research and Development Group (<https://www.edrdg.org/>), and is used in conformance
with the Group's [licence](https://www.edrdg.org/edrdg/licence.html) (CC BY-SA 4.0).

`tools/build_dictionary.py` converts a jmdict-simplified JSON release into the bundled SQLite
file at `app/src/main/assets/dictionary/jmdict.db` - run it again with a newer release to update
the bundled data.
