import { useId } from 'react';

/**
 * Znak MusicClub: nuta, ktorej nozki tworza litere M, i C z glowki drugiej nuty.
 *
 * Gradient dostaje identyfikator z useId, bo gdyby znak pojawil sie na stronie
 * dwa razy, dwa te same "id" w jednym dokumencie zaczelyby sie mieszac.
 *
 * gradientUnits="userSpaceOnUse" jest konieczne: przy domyslnym ustawieniu
 * gradient liczy sie wzgledem pola kazdego ksztaltu osobno, a pole idealnie
 * pionowej kreski ma zerowa szerokosc - nozki M wtedy nie powstaja.
 */
export default function LogoMC({ size = 32, className = '', ...reszta }) {
  const id = useId();
  const farba = `url(#${id})`;

  return (
    <svg
      viewBox="-3.6 1.6 96.8 116.6"
      width={size}
      height={size}
      className={className}
      role="img"
      focusable="false"
      xmlns="http://www.w3.org/2000/svg"
      {...reszta}
    >
      <defs>
        <linearGradient
          id={id}
          gradientUnits="userSpaceOnUse"
          x1="-8" y1="119" x2="108" y2="0"
          gradientTransform="matrix(0.919536, 0, 0, 0.919536, 1.488676, 15.338602)"
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
        <path d="M 24 102.757 L 24 8 L 58 54 L 92 8 L 92 85.718" />
      </g>

      {/* Litera C - przerwa zrobiona linia przerywana, a nie wycieta z ksztaltu */}
      <g transform="matrix(1.099076, 0, 0, 1.169655, -14.792631, -16.749933)">
        <ellipse
          cx="84.776" cy="98.632" rx="14.063" ry="10.49"
          fill="none"
          stroke={farba}
          strokeWidth="7.29555"
          strokeDasharray="57.25 20.11"
          strokeDashoffset="-10.06"
        />
      </g>
    </svg>
  );
}
