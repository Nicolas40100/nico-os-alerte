#!/usr/bin/env python3
from pathlib import Path
import re

import argostranslate.package
import argostranslate.translate

LABELS = Path("direct_native/app/src/main/assets/labels.txt")
OUT = Path("direct_native/app/src/main/assets/labels_fr.tsv")

# Only human synonyms / translation corrections. No brand-name catalogue is added.
# If the model itself contains a brand-specific class (for example Wii), a generic French
# object word may point to it internally; the user still only needs to say the common noun.
OVERRIDES = {
    "shoe": ["chaussure", "chaussures", "basket", "baskets"],
    "sock": ["chaussette", "chaussettes"],
    "printer": ["imprimante"],
    "radiator": ["radiateur", "chauffage"],
    "heater": ["chauffage", "radiateur"],
    "toolbox": ["boite a outils", "caisse a outils"],
    "watering can": ["arrosoir"],
    "dog": ["chien"],
    "rabbit": ["lapin"],
    "stuffed animal": ["peluche", "animal en peluche"],
    "remote control": ["telecommande"],
    "video game": ["jeu video", "console de jeux", "console"],
    "Wii": ["console de jeux", "console"],
    "game controller": ["manette", "manette de jeux"],
    "car": ["voiture"],
    "camera": ["appareil photo", "camera"],
    "cell phone": ["telephone", "telephone portable", "smartphone"],
    "television": ["television", "tele"],
    "vacuum cleaner": ["aspirateur"],
    "coffee maker": ["cafetiere", "machine a cafe"],
}


def clean(text: str) -> str:
    text = text.replace("\t", " ").replace("\n", " ").replace("|", " ")
    text = re.sub(r"\s+", " ", text).strip()
    return text


def install_en_fr():
    argostranslate.package.update_package_index()
    available = argostranslate.package.get_available_packages()
    package = next(
        p for p in available
        if p.from_code == "en" and p.to_code == "fr" and getattr(p, "type", "translate") == "translate"
    )
    argostranslate.package.install_from_path(package.download())


def get_translation():
    langs = argostranslate.translate.get_installed_languages()
    en = next(x for x in langs if x.code == "en")
    fr = next(x for x in langs if x.code == "fr")
    return en.get_translation(fr)


def main():
    labels = [x.strip() for x in LABELS.read_text(encoding="utf-8").splitlines() if x.strip()]
    assert len(labels) == 4585, len(labels)

    install_en_fr()
    translator = get_translation()

    rows = []
    for i, english in enumerate(labels, start=1):
        forms = []
        translated = clean(translator.translate(english))
        if translated:
            forms.append(translated)
        for form in OVERRIDES.get(english, []):
            form = clean(form)
            if form and form.lower() not in {x.lower() for x in forms}:
                forms.append(form)
        if not forms:
            forms.append(english)
        rows.append(f"{english}\t{'|'.join(forms)}")
        if i % 500 == 0:
            print(f"translated {i}/{len(labels)}")

    OUT.write_text("\n".join(rows) + "\n", encoding="utf-8")
    assert len(rows) == 4585
    print(f"wrote {OUT}: {len(rows)} rows")


if __name__ == "__main__":
    main()
