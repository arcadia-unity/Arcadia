using System;
using System.Collections;
using System.Linq;
using System.Text;

namespace Arcadia
{

    public class CrossVersionSynchronizedQueue : Queue
    {
        private readonly Queue queue;

        public override int Count {
            get
            {
                lock (queue)
                {
                    return queue.Count;
                }
            }
        }

        public override bool IsSynchronized
        {
            get
            {
                return true;
            }
        }

        public override object SyncRoot
        {
            get
            {
                return queue.SyncRoot;
            }
        }

        public CrossVersionSynchronizedQueue(Queue queue)
        {
            this.queue = queue;
        }

        public override void CopyTo(Array array, int index)
        {
            lock (queue)
            {
                queue.CopyTo(array, index);
            }
        }

		public override IEnumerator GetEnumerator()
		{
			lock (queue)
			{
				return queue.GetEnumerator();
			}
		}

		public override object Clone()
		{
			lock (queue)
			{
				return new CrossVersionSynchronizedQueue((Queue)queue.Clone());
			}
		}

		public override void Clear()
		{
			lock (queue)
			{
				queue.Clear();
			}
		}

		public override void TrimToSize()
		{
			lock (queue)
			{
				queue.TrimToSize();
			}
		}

		public override bool Contains(object obj) { 
			lock (queue)
			{
				return queue.Contains(obj);
			}
		}

		public override object Dequeue()
		{
			lock (queue)
			{
				return queue.Dequeue();
			}
		}

		public override void Enqueue(object obj)
		{
			lock (queue)
			{
				queue.Enqueue(obj);
			}
		}

		public override object Peek()
		{
			lock (queue)
			{
				return queue.Peek();
			}
		}

		public override object[] ToArray()
		{
			lock (queue)
			{
				return queue.ToArray();
			}
		}


	}
}
