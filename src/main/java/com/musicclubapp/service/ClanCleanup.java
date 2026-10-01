package com.musicclubapp.service;

import com.musicclubapp.repository.ClanJoinRequestRepository;
import com.musicclubapp.repository.ClanMemberTitleRepository;
import com.musicclubapp.repository.ClanMessageReactionRepository;
import com.musicclubapp.repository.ClanPollRepository;
import com.musicclubapp.repository.ClanPollVoteRepository;
import com.musicclubapp.repository.ClanRepository;
import com.musicclubapp.repository.ClanTitleRepository;
import com.musicclubapp.repository.ClanTrackRepository;
import com.musicclubapp.repository.ClanTrackVoteRepository;
import org.springframework.stereotype.Component;

/**
 * Sprzatanie po tym, co klan i jego czlonkowie dopisali "wokol" czatu i postow: reakcje, utwor
 * tygodnia, ankiety, tytuly, prosby o dolaczenie. Jedno miejsce, zeby nowa funkcja klanu nie
 * wymagala szukania, gdzie jeszcze trzeba cos skasowac przy rozwiazaniu klanu i usuwaniu konta.
 *
 * <p>Kolejnosc ma znaczenie: reakcje pod wiadomosciami kasujemy PRZED samymi wiadomosciami,
 * dlatego wolajacy robi to przed kasowaniem czatu.</p>
 */
@Component
public class ClanCleanup {

    private final ClanRepository clans;
    private final ClanMessageReactionRepository chatReactions;
    private final ClanTrackRepository tracks;
    private final ClanTrackVoteRepository trackVotes;
    private final ClanPollRepository polls;
    private final ClanPollVoteRepository pollVotes;
    private final ClanTitleRepository titles;
    private final ClanMemberTitleRepository memberTitles;
    private final ClanJoinRequestRepository requests;

    public ClanCleanup(ClanRepository clans, ClanMessageReactionRepository chatReactions,
                       ClanTrackRepository tracks, ClanTrackVoteRepository trackVotes,
                       ClanPollRepository polls, ClanPollVoteRepository pollVotes,
                       ClanTitleRepository titles, ClanMemberTitleRepository memberTitles,
                       ClanJoinRequestRepository requests) {
        this.clans = clans;
        this.chatReactions = chatReactions;
        this.tracks = tracks;
        this.trackVotes = trackVotes;
        this.polls = polls;
        this.pollVotes = pollVotes;
        this.titles = titles;
        this.memberTitles = memberTitles;
        this.requests = requests;
    }

    /** Wszystko, co nalezy do klanu, poza czatem, postami, zaproszeniami i czlonkostwami. */
    public void ofClan(Long clanId) {
        chatReactions.deleteByClanId(clanId);
        trackVotes.deleteByClanId(clanId);
        tracks.deleteByClanId(clanId);
        pollVotes.deleteByClanId(clanId);
        polls.deleteByClanId(clanId);
        memberTitles.deleteByClanId(clanId);
        titles.deleteByClanId(clanId);
        requests.deleteByClanId(clanId);
        clans.deleteGenres(clanId);
    }

    /**
     * Wszystko, co dopisala ta osoba (i co jest pod jej wiadomosciami, propozycjami i ankietami),
     * przy usuwaniu konta. Wiadomosci na czacie kasuje wolajacy zaraz potem.
     */
    public void ofUser(Long userId) {
        chatReactions.deleteByUserId(userId);
        chatReactions.deleteUnderMessagesOf(userId);
        trackVotes.deleteByUserId(userId);
        trackVotes.deleteUnderTracksOf(userId);
        tracks.deleteByProposerId(userId);
        pollVotes.deleteByUserId(userId);
        pollVotes.deleteUnderPollsOf(userId);
        polls.deleteByAuthorId(userId);
        memberTitles.deleteByUserId(userId);
        requests.deleteByUserId(userId);
    }

    /** Odejscie albo wyrzucenie z klanu: tytuly z tego klanu przestaja miec wlasciciela. */
    public void ofMembership(Long userId, Long clanId) {
        memberTitles.deleteByUserIdAndClanId(userId, clanId);
    }
}
