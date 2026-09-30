package com.musicclubapp.entity;

/**
 * Stan zaproszenia do klanu. Przyjete zaproszenie znika (osoba jest w klanie),
 * odrzucone zostaje jako DECLINED - dzieki temu nikt nie moze zapraszac tej
 * samej osoby bez konca, gdy juz raz odmowila.
 */
public enum InvitationStatus {
    PENDING,
    DECLINED
}
