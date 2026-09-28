import { useId } from 'react';
import { UKLADY } from './napisMCKsztalty';

/**
 * Napis MusicClub, w ktorym litera M i litera C pochodza ze znaku.
 *
 * Dzieki temu obok sygnetu nie stoi drugi raz to samo M i to samo C -
 * znak jest poczatkiem slowa, a nie osobna ozdoba przy nazwie.
 *
 * uklad="poziomy" - calosc w jednej linii; idzie tam, gdzie jest miejsce.
 * uklad="pionowy" - sygnet nietkniety, napis z prawej w dwoch liniach;
 *                   przy tej samej wysokosci jest ponad dwa razy wezszy.
 *
 * Gradient dostaje identyfikator z useId, bo gdyby napis pojawil sie na
 * stronie dwa razy - a tak wlasnie jest w pasku, gdzie oba uklady czekaja
 * obok siebie - dwa te same "id" zaczelyby sie mieszac.
 *
 * gradientUnits="userSpaceOnUse" jest konieczne: przy domyslnym ustawieniu
 * gradient liczy sie wzgledem pola kazdego ksztaltu osobno, a pole idealnie
 * pionowej kreski ma zerowa szerokosc - nozki M wtedy nie powstaja.
 */
export default function NapisMC({ uklad = 'poziomy', height = 36, className = '', ...reszta }) {
  const id = useId();
  const farba = `url(#${id})`;
  const ksztalty = UKLADY[uklad];

  return (
    <svg
      viewBox={ksztalty.viewBox}
      height={height}
      width={Math.round(height * ksztalty.proporcja)}
      className={className}
      role="img"
      aria-label="MusicClub"
      focusable="false"
      xmlns="http://www.w3.org/2000/svg"
      {...reszta}
    >
      <defs>
        <linearGradient
          id={id}
          gradientUnits="userSpaceOnUse"
          x1={ksztalty.gradient.x1}
          y1={ksztalty.gradient.y1}
          x2={ksztalty.gradient.x2}
          y2={ksztalty.gradient.y2}
        >
          <stop offset="0" stopColor="#6d3bd6" />
          <stop offset="0.55" stopColor="#c026d3" />
          <stop offset="1" stopColor="#f43f8e" />
        </linearGradient>
      </defs>

      {/* Glowka nuty */}
      <g transform="matrix(0.954945, 0, 0, 1.072859, 2.194319, -9.895497)">
        <ellipse cx="15.49" cy="101.188" rx="18.391" ry="15.172" fill={farba} />
      </g>

      {/* Litera M - jedna lamana, wiec szczyty i dno V sa ostrymi zalamaniami */}
      <g
        fill="none"
        stroke={farba}
        strokeWidth="14"
        strokeLinejoin="miter"
        strokeLinecap="butt"
        strokeMiterlimit="6"
        transform="matrix(0.8162, 0, 0, 0.8162, 9.244799, 15.380373)"
      >
        <path d={`M 24 102.757 L 24 8 L 58 54 L 92 8 L 92 ${ksztalty.nogaM}`} />
      </g>

      {/* Litera C - przerwa zrobiona linia przerywana, a nie wycieta z ksztaltu */}
      {ksztalty.duzeC ? (
        <ellipse
          cx={ksztalty.duzeC.cx}
          cy={ksztalty.duzeC.cy}
          rx={ksztalty.duzeC.rx}
          ry={ksztalty.duzeC.ry}
          fill="none"
          stroke={farba}
          strokeWidth="11.43"
          strokeDasharray={ksztalty.duzeC.kreska}
          strokeDashoffset={ksztalty.duzeC.przesuniecie}
        />
      ) : (
        <g transform="matrix(1.099076, 0, 0, 1.169655, -14.792631, -16.749933)">
          <ellipse
            cx="84.776"
            cy="98.632"
            rx="14.063"
            ry="10.49"
            fill="none"
            stroke={farba}
            strokeWidth="7.29555"
            strokeDasharray="57.25 20.11"
            strokeDashoffset="-10.06"
          />
        </g>
      )}

      {/* Reszta slowa - krzywe, nie tekst, wiec nie potrzebuje zadnego kroju */}
      <g fill={farba}>
        {ksztalty.litery.map((krzywa) => (
          <path key={krzywa.slice(0, 32)} d={krzywa} />
        ))}
      </g>
    </svg>
  );
}
