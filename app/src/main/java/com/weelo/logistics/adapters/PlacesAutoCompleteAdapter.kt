package com.weelo.logistics.adapters

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import java.util.concurrent.TimeUnit

/**
 * Adapter for AutoCompleteTextView that shows inline location suggestions
 */
class PlacesAutoCompleteAdapter(
    context: Context,
    private val placesClient: PlacesClient
) : ArrayAdapter<String>(context, com.weelo.logistics.R.layout.item_autocomplete_place), Filterable {

    private val predictions = ArrayList<AutocompletePrediction>()
    private val token = AutocompleteSessionToken.newInstance()

    override fun getCount(): Int = predictions.size

    override fun getItem(position: Int): String {
        return predictions[position].getFullText(null).toString()
    }

    fun getPrediction(position: Int): AutocompletePrediction? {
        return if (position >= 0 && position < predictions.size) {
            predictions[position]
        } else null
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                
                if (constraint.isNullOrEmpty() || constraint.length < 2) {
                    results.values = emptyList<String>()
                    results.count = 0
                    return results
                }

                try {
                    // Create request for autocomplete predictions
                    val request = FindAutocompletePredictionsRequest.builder()
                        .setSessionToken(token)
                        .setQuery(constraint.toString())
                        .build()

                    // Fetch predictions with timeout
                    val task = placesClient.findAutocompletePredictions(request)
                    val response = Tasks.await(task, 3, TimeUnit.SECONDS)
                    
                    predictions.clear()
                    predictions.addAll(response.autocompletePredictions)
                    
                    val resultList = predictions.map { it.getFullText(null).toString() }
                    results.values = resultList
                    results.count = resultList.size
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                    results.values = emptyList<String>()
                    results.count = 0
                }

                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (results != null && results.count > 0) {
                    notifyDataSetChanged()
                } else {
                    notifyDataSetInvalidated()
                }
            }
        }
    }
}
