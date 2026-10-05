"""
Wylicza ksztalt ikony IconHug (dwie osoby, ktore sie obejmuja) i wypisuje stale do wklejenia w
frontend/src/components/Icons.jsx.

Dlaczego skrypt, a nie reczny rysunek: barki obu osob sa ciete przerwami wokol rak i miedzy osobami, a tam,
gdzie przerwa przecina luk barku pod ostrym katem, zostaja szpice i cienkie sierpy. Tu przerwy odejmuje sie
jak w edytorze grafiki, a potem kazdy ksztalt "otwiera" (znikaja drzazgi, ostre rogi sie zaokraglaja)
i "zamyka" (lagodnieja wewnetrzne rogi). Glowy zostaja zwyklymi kolami, rece - kreskami z okraglymi koncami.

Uzycie (potrzebny pakiet shapely, tylko do tego skryptu):
    pip install shapely
    python3 frontend/scripts/ikona-przytulas.py
"""
import math

from shapely.geometry import LineString, Point, Polygon
from shapely.ops import unary_union

# --- Parametry (siatka 16x16, przed przesunieciem) ----------------------------------------------
GLOWA_TYL = (5.4, 4.6, 2.25)      # srodek x, y i promien - osoba z tylu (lewa)
GLOWA_PRZOD = (10.9, 4.3, 2.3)    # osoba z przodu (prawa) jest troche wyzsza: nad dolna reka zostaje pelny bark
BARK_TYL = 8.8                    # gora barkow
BARK_PRZOD = 8.2
DOL = 15.3                        # dol obu popiersi
REKA = 1.7                        # grubosc reki
PRZERWA = 0.6                     # odstep wokol rak i miedzy osobami
OTWARCIE = 0.45                   # promien zaokraglenia rogow i usuwania drzazg
ZAMKNIECIE = 0.2                  # promien lagodzenia wewnetrznych rogow
# Reka osoby z przodu na barkach osoby z tylu (luk w gore) i reka osoby z tylu na plecach osoby z przodu
# (luk w dol, wysoko). Poczatek dolnej reki przykrywa rog barku osoby z tylu.
REKA_GORA = ((1.7, 10.9), (3.9, 8.5), (7.6, 7.9), (11.2, 9.0))
REKA_DOL = ((7.6, 10.8), (9.6, 12.8), (12.6, 12.9), (14.7, 11.3))
# Przesuniecie calosci, zeby ikona byla wysrodkowana w polu 16x16
DX, DY = -0.15, -0.65


def krzywa(p0, p1, p2, p3, n=64):
    wynik = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        wynik.append((u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
                      u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1]))
    return wynik


def luk(srodek, r, a0, a1, n=16):
    return [(srodek[0] + r * math.cos(a0 + (a1 - a0) * i / n), srodek[1] + r * math.sin(a0 + (a1 - a0) * i / n))
            for i in range(n + 1)]


def popiersie(x0, x1, srodek, gora, kontrolka):
    """Barki jak w ikonach Bootstrapa: kopula z dwoch krzywych, pionowe boki, dol zaokraglony promieniem 1."""
    pkt = [(x0, DOL - 1), (x0, 12.6)]
    pkt += krzywa((x0, 12.6), (x0, 10.4), (kontrolka, gora), (srodek, gora))[1:]
    pkt += krzywa((srodek, gora), (2 * srodek - kontrolka, gora), (x1, 10.4), (x1, 12.6))[1:]
    pkt += [(x1, DOL - 1)]
    pkt += luk((x1 - 1, DOL - 1), 1, 0, math.pi / 2)[1:]
    pkt += [(x0 + 1, DOL)]
    pkt += luk((x0 + 1, DOL - 1), 1, math.pi / 2, math.pi)[1:]
    return Polygon(pkt)


def gladko(ksztalt):
    ksztalt = ksztalt.buffer(-OTWARCIE, quad_segs=24).buffer(OTWARCIE, quad_segs=24)
    ksztalt = ksztalt.buffer(ZAMKNIECIE, quad_segs=24).buffer(-ZAMKNIECIE, quad_segs=24)
    return ksztalt.simplify(0.006)


def sciezka(ksztalt):
    czesci = getattr(ksztalt, 'geoms', [ksztalt])
    wynik = []
    for wielokat in czesci:
        for pierscien in [wielokat.exterior, *wielokat.interiors]:
            wsp = list(pierscien.coords)[:-1]
            wynik.append('M' + 'L'.join(f'{x + DX:.2f} {y + DY:.2f}' for x, y in wsp) + 'Z')
    return ''.join(wynik)


def kreska(punkty):
    p = [f'{x + DX:.2f} {y + DY:.2f}' for x, y in punkty]
    return f'M{p[0]}C{p[1]} {p[2]} {p[3]}'


def main():
    glowa_przod = Point(GLOWA_PRZOD[:2]).buffer(GLOWA_PRZOD[2], quad_segs=48)
    przod = popiersie(8.3, 15.2, 11.7, BARK_PRZOD, 9.7)
    tyl = popiersie(0.8, 9.2, 5.0, BARK_TYL, 2.6)
    gora = LineString(krzywa(*REKA_GORA))
    dol = LineString(krzywa(*REKA_DOL))

    # Osoba z tylu: bez miejsca zajetego przez osobe z przodu i bez pasa wokol reki na jej barkach
    tyl = tyl.difference(unary_union([glowa_przod, przod]).buffer(PRZERWA, quad_segs=24))
    tyl = tyl.difference(gora.buffer(REKA / 2 + PRZERWA, quad_segs=24))
    # Osoba z przodu: bez pasa wokol reki, ktora ja obejmuje
    przod = przod.difference(dol.buffer(REKA / 2 + PRZERWA, quad_segs=24))

    print(f"<circle cx=\"{GLOWA_TYL[0] + DX:g}\" cy=\"{GLOWA_TYL[1] + DY:g}\" r=\"{GLOWA_TYL[2]:g}\" />")
    print(f"<circle cx=\"{GLOWA_PRZOD[0] + DX:g}\" cy=\"{GLOWA_PRZOD[1] + DY:g}\" r=\"{GLOWA_PRZOD[2]:g}\" />")
    print(f"const HUG_TYL = '{sciezka(gladko(tyl))}';")
    print(f"const HUG_PRZOD = '{sciezka(gladko(przod))}';")
    print(f"const HUG_RAMIE_GORA = '{kreska(REKA_GORA)}';")
    print(f"const HUG_RAMIE_DOL = '{kreska(REKA_DOL)}';")


if __name__ == '__main__':
    main()
