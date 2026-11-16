package dev.magisk.tiktokregionspoof.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dev.magisk.tiktokregionspoof.databinding.BottomSheetCarrierPickerBinding
import dev.magisk.tiktokregionspoof.databinding.ItemCarrierBinding
import dev.magisk.tiktokregionspoof.model.CarrierProfile
import dev.magisk.tiktokregionspoof.model.CarrierRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CarrierPickerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCarrierPickerBinding? = null
    private val binding get() = _binding!!

    private var allCarriers: List<CarrierProfile> = emptyList()
    private var filterJob: Job? = null

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

        val adapter = CarrierAdapter { chosen ->
            setFragmentResult(
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
            adapter.submitList(allCarriers)
        }

        binding.etSearchCarrier.doAfterTextChanged { text ->
            val q = text?.toString()?.trim().orEmpty()
            filterJob?.cancel()
            filterJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(150)
                val filtered = withContext(Dispatchers.Default) {
                    if (q.isEmpty()) {
                        allCarriers
                    } else {
                        allCarriers.filter {
                            it.country.contains(q, ignoreCase = true) ||
                                it.cc2.contains(q, ignoreCase = true) ||
                                it.brand.contains(q, ignoreCase = true) ||
                                it.operator.contains(q, ignoreCase = true) ||
                                it.mccmnc.contains(q, ignoreCase = true)
                        }
                    }
                }
                adapter.submitList(filtered)
            }
        }
    }

    override fun onDestroyView() {
        filterJob?.cancel()
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
                binding.tvCarrierTitle.text = item.displayTitle
                binding.tvCarrierSubtitle.text = item.displaySubtitle
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

        fun newInstance(): CarrierPickerBottomSheet = CarrierPickerBottomSheet()
    }
}
