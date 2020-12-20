package xyz.divineugorji.mymemory

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso
import xyz.divineugorji.mymemory.models.BoardSize
import xyz.divineugorji.mymemory.models.MemoryGame
import xyz.divineugorji.mymemory.models.UserImageList
import xyz.divineugorji.mymemory.utils.EXTRA_BOARD_SIZE
import xyz.divineugorji.mymemory.utils.EXTRA_GAME_NAME
import java.text.DecimalFormat
import java.text.NumberFormat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 248
    }

    private var timer: CountDownTimer? = null
    private var mSoundPlayer: MediaPlayer? = null
    private val db = Firebase.firestore
    private var gameName: String? = null
    private var customGameImages: List<String>? = null
    private var boardSize: BoardSize = BoardSize.EASY
    private lateinit var clRoot: CoordinatorLayout
    private lateinit var rvBoard: RecyclerView
    private lateinit var tvNumMoves: TextView


    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter: MemoryBoardAdapter
    private lateinit var tvNumPairs: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var textView: TextView? = null
        textView = findViewById<TextView>(R.id.textView)

        clRoot = findViewById(R.id.clRoot)
        rvBoard = findViewById(R.id.rvBoard)

        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)

        setUpBoard()

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mi_refresh -> {
                if (memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame()) {
                    showAlertDialog("Quit current game level?", null, View.OnClickListener {
                        mSoundPlayer?.stop()
                        stopTimer()
                        setUpBoard()
                    })
                } else {
                    mSoundPlayer?.stop()
                    stopTimer()
                    setUpBoard()
                }
                return true
            }
            R.id.mi_new_size -> {
                showSizeDialog()
                return true
            }
            R.id.mi_custom -> {
                showCreationDialog()
                return true
            }
            R.id.mi_download -> {
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
            if (customGameName == null) {
                Log.e(TAG, "Got null custom game from CreateActivity")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showDownloadDialog() {
        stopTimer()
        mSoundPlayer?.stop()
        val boardDownloadView = LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog("Fetch memory game", boardDownloadView, View.OnClickListener {
            val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.etGameName)
            val gameToDownload = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        })

    }

    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            val userImageList = document.toObject(UserImageList::class.java)
            if (userImageList?.images == null) {
                Log.e(TAG, "Invalid custom game data from Firebase")
                Snackbar.make(clRoot, "Sorry, we couldn't find any such game, " + "'$customGameName'", Snackbar.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            val numCards = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images
            for (imageUrl in userImageList.images) {
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(clRoot, "You're now playing '$customGameName'!", Snackbar.LENGTH_LONG).show()
            gameName = customGameName
            setUpBoard()
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Exception when retrieving game", exception)
        }
    }

    private fun showCreationDialog() {
        mSoundPlayer?.stop()
        stopTimer()
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        showAlertDialog("Create your own memory board", boardSizeView, View.OnClickListener {
            val desiredBoardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rEasy -> BoardSize.EASY
                R.id.rMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            val intent = Intent(this, CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSize)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        })
    }

    private fun showSizeDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize: RadioGroup = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        when (boardSize) {
            BoardSize.EASY -> radioGroupSize.check(R.id.rEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rMedium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rHard)
        }

        showAlertDialog("Choose new size", boardSizeView, View.OnClickListener {
            boardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rEasy -> BoardSize.EASY
                R.id.rMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            gameName = null
            customGameImages = null
            stopTimer()
            mSoundPlayer?.stop()
            setUpBoard()
        })
    }

    private fun showAlertDialog(title: String, view: View?, positiveButtonClickListener: View.OnClickListener) {
        AlertDialog.Builder(this)
                .setTitle(title)
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Ok") { _, _ ->
                    positiveButtonClickListener.onClick(null)
                }.show()
    }

    private fun setUpBoard() {
        supportActionBar?.title = gameName ?: getString(R.string.app_name)
        playGameOnSound()
        myCounter()
        when (boardSize) {
            BoardSize.EASY -> {
                tvNumMoves.text = "Easy: 4 * 2"
                tvNumPairs.text = "Pairs: 0 / 4"
            }

            BoardSize.MEDIUM -> {
                tvNumMoves.text = "Medium: 6 * 3"
                tvNumPairs.text = "Pairs: 0 / 9"
            }
            BoardSize.HARD -> {
                tvNumMoves.text = "Hard: 6 * 6"
                tvNumPairs.text = "Pairs: 0 / 12"
            }
        }
        tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))
        memoryGame = MemoryGame(boardSize, customGameImages)
        adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object : MemoryBoardAdapter.CardClickListener {
            override fun onCardClicked(position: Int) {
                Log.i(TAG, "card clicked $position")
                updateGameWithFlip(position)
            }
        })

        rvBoard.adapter = adapter
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    private fun playGameOnSound() {
        mSoundPlayer = MediaPlayer.create(this, R.raw.game_music)
        mSoundPlayer?.start()
    }

    private fun playGameOverSound() {
        mSoundPlayer = MediaPlayer.create(this, R.raw.game_over)
        mSoundPlayer?.start()
    }

    private fun updateGameWithFlip(position: Int) {
        if (memoryGame.haveWonGame()) {
            //Alert the user of an invalid move
            Snackbar.make(clRoot, "You already won!", Snackbar.LENGTH_LONG).show()
            return
        }
        if (memoryGame.isCardFacedUp(position)) {
            //Alert the user of an invalid move
            Snackbar.make(clRoot, "Invalid move!", Snackbar.LENGTH_SHORT).show()
            return
        }

        if (memoryGame.flipCard(position)) {
            Log.i(TAG, "Found a match! Num pairs found &{memoryGame.numPairsFound}")
            val color = ArgbEvaluator().evaluate(
                    memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
                    ContextCompat.getColor(this, R.color.color_progress_none),
                    ContextCompat.getColor(this, R.color.color_progress_full)
            ) as Int
            tvNumPairs.setTextColor(color)
            tvNumPairs.text = "Pairs: ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"
            if (memoryGame.haveWonGame()) {
                Snackbar.make(clRoot, "You won! Congratulations.", Snackbar.LENGTH_LONG).show()
                stopTimer()
                playWonGameSound()
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.YELLOW, Color.GREEN, Color.MAGENTA)).oneShot()
               mSoundPlayer?.stop()

            }
        }
        tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()
    }

    private fun playWonGameSound() {
        val mPlayer: MediaPlayer = MediaPlayer.create(this, R.raw.party_sound)
        mPlayer.start()
    }

    fun myCounter() {
        var textView: TextView? = null
        textView = findViewById<TextView>(R.id.textView)
        // Time is in millisecond so 50sec = 50000 I have used
        // countdown Interveal is 1sec = 1000 I have used
        timer = object : CountDownTimer(40000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Used for formatting digit to be in 2 digits only
                val f: NumberFormat = DecimalFormat("00")
                val hour = millisUntilFinished / 3600000 % 24
                val min = millisUntilFinished / 60000 % 60
                val sec = millisUntilFinished / 1000 % 60
                textView.setText("Time remaining:" + f.format(hour) + ":" + f.format(min) + ":" + f.format(sec))
            }

            // When the task is over it will print 00:00:00 there
            override fun onFinish() {
                if (!memoryGame.haveWonGame()){
                    mSoundPlayer?.stop()
                    textView.setText("00:00:00")
                    playGameOverSound()
                    showGameOverDialog("Game Over", null, View.OnClickListener {
                        setUpBoard()
                    })

                }
            }
        }.start()
    }

    private fun showGameOverDialog(title: String, view: View?, positiveButtonClickListener: View.OnClickListener) {
        AlertDialog.Builder(this)
                .setTitle(title)
                .setView(view)
                .setNegativeButton("Quit Game"){ _,_ ->
                    finish()
                }
                .setPositiveButton("Play again"){_, _ ->
                    positiveButtonClickListener.onClick(null)
                }.show()
    }

    private fun stopTimer(){
        var textView: TextView? = null
        textView = findViewById<TextView>(R.id.textView)
        timer?.cancel()
        textView.setText("00:00:00")
    }

}

