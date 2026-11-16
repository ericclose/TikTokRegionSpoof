package dev.magisk.tiktokregionspoof.ui

import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Filter
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dev.magisk.tiktokregionspoof.R
import dev.magisk.tiktokregionspoof.databinding.BottomSheetCarrierPickerBinding
import dev.magisk.tiktokregionspoof.databinding.ItemCarrierBinding
import dev.magisk.tiktokregionspoof.model.CarrierFilterEngine
import dev.magisk.tiktokregionspoof.model.CarrierFilterEngine.FilterOption
import dev.magisk.tiktokregionspoof.model.CarrierProfile
import dev.magisk.tiktokregionspoof.model.CarrierRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CarrierPickerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCarrierPickerBinding? = null
    private val binding get() = _binding!!

    private var allCarriers: List<CarrierProfile> = emptyList()
    private var countryOptions: List<FilterOption> = emptyList()
    private var carrierOptions: List<FilterOption> = emptyList()
    private var selectedCountryIso: String = ""
    private var selectedCarrierBrand: String = ""
    private var suppressTextEvents: Boolean = false
    private lateinit var adapter: CarrierAdapter

    private class DropdownFilterAdapter(
        context: Context,
        private val items: List<FilterOption>
    ) : ArrayAdapter<FilterOption>(context, android.R.layout.simple_dropdown_item_1line, items) {
        private val customFilter = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.trim().orEmpty()
                val matchesExact = items.any { it.label.equals(query, ignoreCase = true) }
                val filtered = if (query.isEmpty() || matchesExact) {
                    items
                } else {
                    items.filter { it.label.contains(query, ignoreCase = true) }
                }
                return FilterResults().apply {
                    values = filtered
                    count = filtered.size
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                clear()
                val list = results?.values as? List<FilterOption> ?: items
                addAll(list)
                notifyDataSetChanged()
            }
        }

        override fun getFilter(): Filter = customFilter
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCarrierPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CarrierAdapter { chosen ->
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                Bundle().apply {
                    putString(KEY_CC2, chosen.cc2)
                    putString(KEY_COUNTRY, chosen.country)
                    putString(KEY_MCCMNC, chosen.mccmnc)
                    putString(KEY_BRAND, chosen.brand)
                    putString(KEY_OPERATOR, chosen.operator)
                }
            )
            dismiss()
        }

        binding.rvCarriers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCarriers.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            allCarriers = withContext(Dispatchers.IO) {
                CarrierRepository.loadAll(requireContext().applicationContext)
            }
            if (_binding == null) return@launch

            rebuildDropdownAdapters(updateCountry = true, updateCarrier = true)
            bindDropdown(
                field = binding.actvFilterCountry,
                allLabelRes = R.string.all_countries,
                optionsProvider = { countryOptions },
                getSelected = { selectedCountryIso },
                setSelected = { selectedCountryIso = it },
                onChanged = { updateOpposite ->
                    if (updateOpposite) rebuildDropdownAdapters(updateCountry = false, updateCarrier = true)
                    binding.btnResetFilters.setOnClickListener {
                suppressTextEvents = true
                selectedCountryIso = ""
                selectedCarrierBrand = ""
                binding.actvFilterCountry.setText("", false)
                binding.actvFilterCarrier.setText("", false)
                rebuildDropdownAdapters(updateCountry = true, updateCarrier = true)
                applyFilters()
                it.post { suppressTextEvents = false }
            }

            applyFilters()
                }
            )
            bindDropdown(
                field = binding.actvFilterCarrier,
                allLabelRes = R.string.all_carriers,
                optionsProvider = { carrierOptions },
                getSelected = { selectedCarrierBrand },
                setSelected = { selectedCarrierBrand = it },
                onChanged = { updateOpposite ->
                    if (updateOpposite) rebuildDropdownAdapters(updateCountry = true, updateCarrier = false)
                    binding.btnResetFilters.setOnClickListener {
                suppressTextEvents = true
                selectedCountryIso = ""
                selectedCarrierBrand = ""
                binding.actvFilterCountry.setText("", false)
                binding.actvFilterCarrier.setText("", false)
                rebuildDropdownAdapters(updateCountry = true, updateCarrier = true)
                applyFilters()
                it.post { suppressTextEvents = false }
            }

            applyFilters()
                }
            )

            binding.btnResetFilters.setOnClickListener {
                suppressTextEvents = true
                selectedCountryIso = ""
                selectedCarrierBrand = ""
                binding.actvFilterCountry.setText("", false)
                binding.actvFilterCarrier.setText("", false)
                rebuildDropdownAdapters(updateCountry = true, updateCarrier = true)
                applyFilters()
                it.post { suppressTextEvents = false }
            }

            applyFilters()
        }
    }

    private fun bindDropdown(
        field: AutoCompleteTextView,
        allLabelRes: Int,
        optionsProvider: () -> List<FilterOption>,
        getSelected: () -> String,
        setSelected: (String) -> Unit,
        onChanged: (updateOpposite: Boolean) -> Unit
    ) {
        val allLabel = getString(allLabelRes)
        var lastRawText = ""
        field.setOnItemClickListener { parent, _, pos, _ ->
            val option = parent.getItemAtPosition(pos) as? FilterOption
            suppressTextEvents = true
            lastRawText = option?.label.orEmpty()
            setSelected(option?.value.orEmpty())
            onChanged(true)
            field.post { suppressTextEvents = false }
        }
        field.doAfterTextChanged { text ->
            if (suppressTextEvents) return@doAfterTextChanged
            val raw = text?.toString()?.trim().orEmpty()
            val prevSelected = getSelected()
            val prevText = lastRawText
            lastRawText = raw
            val next = if (raw.isEmpty() || raw.equals(allLabel, ignoreCase = true)) {
                ""
            } else {
                optionsProvider().firstOrNull { it.label.equals(raw, ignoreCase = true) }?.value ?: ""
            }
            setSelected(next)
            onChanged(raw.isEmpty() && (prevSelected.isNotEmpty() || prevText.isNotEmpty()))
        }
    }

    private fun rebuildDropdownAdapters(updateCountry: Boolean, updateCarrier: Boolean) {
        val allCountriesLabel = getString(R.string.all_countries)
        val allCarriersLabel = getString(R.string.all_carriers)
        val countryText = binding.actvFilterCountry.text?.toString()?.trim().orEmpty()
        val carrierText = binding.actvFilterCarrier.text?.toString()?.trim().orEmpty()

        if (updateCountry) {
            countryOptions = CarrierFilterEngine.buildCountryOptions(
                allCarriers = allCarriers,
                carrierBrand = selectedCarrierBrand,
                carrierText = carrierText,
                allCountriesLabel = allCountriesLabel,
                allCarriersLabel = allCarriersLabel
            )
            binding.actvFilterCountry.setAdapter(
                DropdownFilterAdapter(requireContext(), countryOptions.toMutableList())
            )
        }

        if (updateCarrier) {
            carrierOptions = CarrierFilterEngine.buildCarrierOptions(
                allCarriers = allCarriers,
                countryIso = selectedCountryIso,
                countryText = countryText,
                allCountriesLabel = allCountriesLabel,
                allCarriersLabel = allCarriersLabel
            )
            binding.actvFilterCarrier.setAdapter(
                DropdownFilterAdapter(requireContext(), carrierOptions.toMutableList())
            )
        }
    }

    private fun applyFilters() {
        val allCountriesLabel = getString(R.string.all_countries)
        val allCarriersLabel = getString(R.string.all_carriers)
        val countryText = binding.actvFilterCountry.text?.toString()?.trim().orEmpty()
        val carrierText = binding.actvFilterCarrier.text?.toString()?.trim().orEmpty()

        val filtered = CarrierFilterEngine.filterCarriers(
            allCarriers = allCarriers,
            countryIso = selectedCountryIso,
            countryText = countryText,
            allCountriesLabel = allCountriesLabel,
            carrierBrand = selectedCarrierBrand,
            carrierText = carrierText,
            allCarriersLabel = allCarriersLabel
        )
        binding.tvCarrierCount.text = resources.getQuantityString(
            R.plurals.carrier_count,
            filtered.size,
            filtered.size
        )
        val isEmpty = filtered.isEmpty()
        binding.rvCarriers.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        adapter.submitList(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class CarrierAdapter(
        private val onClick: (CarrierProfile) -> Unit
    ) : ListAdapter<CarrierProfile, CarrierAdapter.VH>(DIFF_CALLBACK) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemCarrierBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        inner class VH(private val binding: ItemCarrierBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(item: CarrierProfile) {
                binding.tvCarrierFlag.text = item.flagEmoji
                binding.tvCarrierTitle.text = itemView.context.getString(
                    R.string.carrier_title_format,
                    item.country,
                    item.brand
                )
                binding.tvCarrierSubtitle.text = item.operator.ifBlank { item.cc2.uppercase() }
                binding.tvCarrierMccMnc.text = item.mccmnc
                binding.root.setOnClickListener { onClick(item) }
            }
        }

        companion object {
            private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CarrierProfile>() {
                override fun areItemsTheSame(oldItem: CarrierProfile, newItem: CarrierProfile): Boolean =
                    oldItem.cc2 == newItem.cc2 &&
                        oldItem.mccmnc == newItem.mccmnc &&
                        oldItem.brand == newItem.brand

                override fun areContentsTheSame(oldItem: CarrierProfile, newItem: CarrierProfile): Boolean =
                    oldItem == newItem
            }
        }
    }

    companion object {
        const val TAG = "carrier_picker"
        const val REQUEST_KEY = "carrier_picker_result"
        const val KEY_CC2 = "cc2"
        const val KEY_COUNTRY = "country"
        const val KEY_MCCMNC = "mccmnc"
        const val KEY_BRAND = "brand"
        const val KEY_OPERATOR = "operator"
    }
}
