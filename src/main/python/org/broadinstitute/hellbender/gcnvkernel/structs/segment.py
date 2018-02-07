from typing import Optional
from ..io import io_consts
from .. import config


class IntegerCopyNumberSegment:
    """Represents a constant copy-number genomic interval ("segment") and stores various quality metrics."""
    def __init__(self,
                 contig: str, start: int, end: int,
                 num_spanning_intervals: int,
                 call_copy_number: int,
                 baseline_copy_number: int):
        self.contig = contig
        self.start = start
        self.end = end
        self.call_copy_number = call_copy_number
        self.num_spanning_intervals = num_spanning_intervals
        self.baseline_copy_number = baseline_copy_number
        self.some_quality: Optional[float] = None
        self.exact_quality: Optional[float] = None
        self.start_quality: Optional[float] = None
        self.end_quality: Optional[float] = None

    @staticmethod
    def get_header_column_string():
        return '\t'.join([io_consts.contig_column_name,
                          io_consts.start_column_name,
                          io_consts.end_column_name,
                          io_consts.num_spanning_intervals_column_name,
                          io_consts.call_copy_number_column_name,
                          io_consts.baseline_copy_number_column_name,
                          io_consts.some_quality_column_name,
                          io_consts.exact_quality_column_name,
                          io_consts.start_quality_column_name,
                          io_consts.end_quality_column_name])

    @staticmethod
    def _repr_quality(quality):
        return '{0:.{1}f}'.format(quality, config.phred_decimals) if quality is not None else '.'

    def __repr__(self):
        return '\t'.join([self.contig,
                          repr(self.start),
                          repr(self.end),
                          repr(self.num_spanning_intervals),
                          repr(self.call_copy_number),
                          repr(self.baseline_copy_number),
                          self._repr_quality(self.some_quality),
                          self._repr_quality(self.exact_quality),
                          self._repr_quality(self.start_quality),
                          self._repr_quality(self.end_quality)])
