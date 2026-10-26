package apap.oneverandah.chat

import apap.oneverandah.api.StudyExamBeat
import apap.oneverandah.api.StudyExamBeatKind
import apap.oneverandah.api.StudyExamBeatScore
import apap.oneverandah.api.StudyExamGrade
import apap.oneverandah.model.Language
import kotlin.math.roundToInt

object StudyExamScoring {
    fun feedback(languageTag: String): String = when (Language.fromTag(languageTag)) {
        Language.ENGLISH -> "Your score reflects how well you understood and used the study sentences."
        Language.VIETNAMESE -> "Điểm số phản ánh khả năng hiểu và sử dụng các câu đã học."
        Language.CHINESE -> "此成绩反映你理解并运用所学句子的能力。"
        Language.JAPANESE -> "このスコアは、学習した文の理解力と使い方を反映しています。"
        Language.KOREAN -> "이 점수는 학습한 문장을 이해하고 활용하는 능력을 반영합니다."
        Language.RUSSIAN -> "Оценка отражает, насколько вы понимаете и используете изученные фразы."
        Language.SPANISH -> "La nota refleja cómo comprendes y utilizas las frases estudiadas."
        Language.GERMAN -> "Die Note zeigt, wie gut du die gelernten Sätze verstehst und verwendest."
        Language.BURMESE -> "ဤအမှတ်သည် သင်လေ့လာထားသော စာကြောင်းများကို နားလည်ပြီး အသုံးပြုနိုင်မှုကို ဖော်ပြသည်။"
    }

    fun level(points: Int, maxPoints: Int): Int {
        if (maxPoints <= 0) return 1
        val percentage = points.coerceIn(0, maxPoints) * 100 / maxPoints
        return when {
            percentage <= 20 -> 1
            percentage <= 40 -> 2
            percentage <= 60 -> 3
            percentage <= 80 -> 4
            else -> 5
        }
    }

    fun grade(
        beats: List<StudyExamBeat>,
        beatScores: List<StudyExamBeatScore>,
        scoredAt: Long,
        feedback: String,
    ): StudyExamGrade {
        val scoresById = beatScores.associateBy { it.beatId }
        val listeningBeats = beats.filter { it.kind == StudyExamBeatKind.LISTEN }
        val speakingBeats = beats.filter { it.kind == StudyExamBeatKind.SPEAK }
        val listening = skillLevel(listeningBeats, scoresById)
        val speaking = skillLevel(speakingBeats, scoresById)
        val overall = when {
            listeningBeats.isEmpty() -> speaking
            speakingBeats.isEmpty() -> listening
            else -> ((listening + speaking) / 2.0).roundToInt().coerceIn(1, 5)
        }
        return StudyExamGrade(
            overall = overall,
            listening = listening,
            speaking = speaking,
            feedback = feedback.trim(),
            scoredAt = scoredAt,
        )
    }

    private fun skillLevel(
        beats: List<StudyExamBeat>,
        scoresById: Map<String, StudyExamBeatScore>,
    ): Int {
        val maxPoints = beats.size * 2
        val points = beats.sumOf { beat -> scoresById[beat.id]?.points?.coerceIn(0, 2) ?: 0 }
        return level(points, maxPoints)
    }
}
