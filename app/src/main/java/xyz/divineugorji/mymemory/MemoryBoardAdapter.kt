package xyz.divineugorji.mymemory

import android.content.ContentValues.TAG
import android.content.Context
import android.content.res.ColorStateList
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import xyz.divineugorji.mymemory.models.BoardSize
import xyz.divineugorji.mymemory.models.MemoryCard
import kotlin.math.min


class MemoryBoardAdapter(
        private val context: Context,
        private val boardSize: BoardSize,
        private val cards: List<MemoryCard>,
        private val cardClickListener: CardClickListener

        ) :
        RecyclerView.Adapter<MemoryBoardAdapter.ViewHolder>() {


    companion object{
        private const val MARGIN_SIZE = 10
    }

    interface CardClickListener{
        fun onCardClicked(position: Int)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardWith: Int = parent.width / boardSize.getWidth() - (2* MARGIN_SIZE)
        val cardHeight: Int = parent.height / boardSize.getHeight() - (2* MARGIN_SIZE)
        val cardSIdeLength: Int = min(cardWith, cardHeight)
        val view =  LayoutInflater.from(context).inflate(R.layout.memory_card, parent, false)
        val layoutParams : ViewGroup.MarginLayoutParams = view.findViewById<CardView>(R.id.card_view).layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.width = cardSIdeLength
        layoutParams.height = cardSIdeLength
        layoutParams.setMargins(MARGIN_SIZE, MARGIN_SIZE, MARGIN_SIZE, MARGIN_SIZE)
        return ViewHolder(view)
    }

    override fun getItemCount() = boardSize.numCards

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
       holder.bind(position)
    }


    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageButton = itemView.findViewById<ImageButton>(R.id.imageButton)

        fun bind(position: Int) {
            val memoryCard: MemoryCard = cards[position]
            if (memoryCard.isFaceUp){
                if (memoryCard.imageUrl != null) {
                    Picasso.get().load(memoryCard.imageUrl).placeholder(R.drawable.ic_image).into(imageButton)
                }else{
                    imageButton.setImageResource(memoryCard.indentifier)
                }
            }else{
                imageButton.setImageResource(R.drawable.mymemory)

            }

            imageButton.alpha = if (memoryCard.isMatched) .4f else 1.0f

            val colorStateList: ColorStateList? = if (memoryCard.isMatched)
                        ContextCompat.getColorStateList(context, R.color.color_grey) else null
            ViewCompat.setBackgroundTintList(imageButton, colorStateList)

            imageButton.setOnClickListener {
                Log.i(TAG, "clicked on position $position")
                cardClickListener.onCardClicked(position)
            }
        }
    }
}
